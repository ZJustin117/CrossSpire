package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CentralQueueManager {

    private static final class QueueState {
        final List<Protocol.QueueSubmitMessage> queue = Collections.synchronizedList(new ArrayList<Protocol.QueueSubmitMessage>());
        boolean processing;
    }
    private final Map<String, QueueState> queues = new HashMap<String, QueueState>();

    private static final Comparator<Protocol.QueueSubmitMessage> ORDER = new Comparator<Protocol.QueueSubmitMessage>() {
        @Override
        public int compare(Protocol.QueueSubmitMessage a, Protocol.QueueSubmitMessage b) {
            if (a.timestamp != b.timestamp) return Long.compare(a.timestamp, b.timestamp);
            return a.senderId.compareTo(b.senderId);
        }
    };

    public void onQueueSubmit(Protocol.QueueSubmitMessage pkt) {
        String partyId = partyId(pkt.partyId);
        pkt.partyId = partyId;
        if (!CombatPhase.allowsQueueSubmit(CombatPhaseCoordinator.getCurrentPhase(partyId))) {
            CSLog.log("CentralQueueManager reject submit in phase="
                + CombatPhaseCoordinator.getCurrentPhase(partyId) + " card=" + pkt.cardId);
            return;
        }
        QueueState state = stateFor(partyId);
        String dedupKey = pkt.senderId + "/" + pkt.seq;
        pkt.packetId = dedupKey;
        boolean becameNonEmpty;
        synchronized (state.queue) {
            for (Protocol.QueueSubmitMessage existing : state.queue) {
                if (existing.packetId != null && existing.packetId.equals(dedupKey)) return;
            }
            becameNonEmpty = state.queue.isEmpty();
            state.queue.add(pkt);
            Collections.sort(state.queue, ORDER);
        }
        CSLog.log("CentralQueueManager added party=" + partyId + ": " + pkt.cardId + " size=" + state.queue.size());
        if (becameNonEmpty) {
            CombatPhaseCoordinator.broadcast(partyId, CombatPhase.RESOLVING_QUEUE);
        }
        broadcastUpdate(partyId);
        if (!state.processing) processNext(partyId);
    }

    public Protocol.QueueSubmitMessage dequeue() {
        return dequeue(PartyManager.DEFAULT_PARTY_ID);
    }
    public Protocol.QueueSubmitMessage dequeue(String partyId) {
        QueueState state = stateFor(partyId);
        synchronized (state.queue) { if (state.queue.isEmpty()) return null; return state.queue.remove(0); }
    }

    public int size() {
        return size(PartyManager.DEFAULT_PARTY_ID);
    }
    public int size(String partyId) {
        QueueState state = stateFor(partyId);
        synchronized (state.queue) { return state.queue.size(); }
    }

    public Protocol.QueueEntry[] getEntries() {
        return getEntries(PartyManager.DEFAULT_PARTY_ID);
    }
    public Protocol.QueueEntry[] getEntries(String partyId) {
        QueueState state = stateFor(partyId);
        synchronized (state.queue) {
            Protocol.QueueEntry[] entries = new Protocol.QueueEntry[state.queue.size()];
            for (int i = 0; i < state.queue.size(); i++) {
                entries[i] = toEntry(state.queue.get(i), i == 0 && state.processing ? "executing" : "pending");
            }
            return entries;
        }
    }

    public void setExecuting(String cardId) {
        broadcastUpdate(PartyManager.DEFAULT_PARTY_ID);
    }

    public void markDone(String packetId) {
        markDone(PartyManager.DEFAULT_PARTY_ID, packetId);
    }
    public void markDone(String partyId, String packetId) {
        QueueState state = stateFor(partyId);
        boolean wasEmpty;
        synchronized (state.queue) {
            for (int i = 0; i < state.queue.size(); i++) {
                if (packetId.equals(state.queue.get(i).packetId)) {
                    state.queue.remove(i);
                    break;
                }
            }
            wasEmpty = state.queue.isEmpty();
        }
        broadcastUpdate(partyId);
        if (wasEmpty) {
            broadcastQueueEmpty(partyId);
        } else {
            processNext(partyId);
        }
    }

    private void processNext(String partyId) {
        QueueState state = stateFor(partyId);
        Protocol.QueueSubmitMessage head;
        synchronized (state.queue) {
            if (state.queue.isEmpty()) { state.processing = false; return; }
            state.processing = true;
            head = state.queue.get(0);
        }

        CSLog.log("CentralQueueManager process: " + head.cardId + " owner=" + head.ownerId);

        if (head.ownerId != null && head.ownerId.equals(CrossSpireMod.playerId)) {
            handleOwnItem(partyId, head);
        } else {
            sendInvoke(partyId, head);
        }
    }

    private void handleOwnItem(String partyId, Protocol.QueueSubmitMessage head) {
        CSLog.log("CentralQueueManager own item: " + head.cardId + " target=" + head.gameTarget);
        crossspire.sync.LocalCapturePatches.pushSuppress();
        crossspire.reference.LocalReference<Object> ref = new crossspire.reference.LocalReference<Object>(head.cardId, CrossSpireMod.playerId);
        ref.dereference(head.gameTarget);

        if (head.packetId != null) markDone(partyId, head.packetId);
    }

    private void sendInvoke(String partyId, Protocol.QueueSubmitMessage head) {
        Protocol.InvokeMessage invoke = new Protocol.InvokeMessage();
        invoke.source = CrossSpireMod.playerId;
        invoke.target = head.ownerId;
        invoke.seq = CrossSpireMod.nextSeq();
        invoke.partyId = partyId;
        invoke.refId = "card:" + head.cardId + "@" + head.ownerId;
        invoke.trigger = "play_card";
        invoke.args = head.gameTarget;

        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.send(Protocol.GSON.toJson(invoke));
            CSLog.log("CentralQueueManager sent invoke to " + head.ownerId.substring(0, 8)
                + " for " + head.cardId);
        }
    }

    public void onInvokeResult(Protocol.InvokeResultMessage result) {
        CSLog.log("CentralQueueManager invoke_result: " + result.refId);

        Protocol.QueueComplete complete = new Protocol.QueueComplete();
        complete.source = result.source != null ? result.source : CrossSpireMod.playerId;
        complete.executorId = complete.source;
        complete.seq = CrossSpireMod.nextSeq();
        complete.type = "combat_result";
        complete.partyId = partyId(result.partyId);
        complete.packetId = result.refId;
        complete.effects = result.effects;
        complete.operationSequence = result.operationSequence;

        String cardId = result.refId.contains("@") ? result.refId.split(":")[1].split("@")[0] : "unknown";
        String partyId = partyId(result.partyId);
        QueueState state = stateFor(partyId);
        synchronized (state.queue) {
            for (Protocol.QueueSubmitMessage q : state.queue) {
                if (q.ownerId.equals(result.source) && q.cardId.equals(cardId)) {
                    markDone(partyId, q.packetId);
                    return;
                }
            }
        }
    }

    public void onCombatResultReplayed(String sourceCardId) {
        QueueState state = stateFor(PartyManager.DEFAULT_PARTY_ID);
        state.processing = false;
        processNext(PartyManager.DEFAULT_PARTY_ID);
    }

    private void broadcastUpdate(String partyId) {
        if (!CrossSpireMod.isConnected()) return;

        Protocol.QueueUpdateMessage update = new Protocol.QueueUpdateMessage();
        update.source = CrossSpireMod.playerId;
        update.seq = CrossSpireMod.nextSeq();
        update.partyId = partyId(partyId);
        update.entries = getEntries(partyId);
        CrossSpireMod.sendToParty(partyId(partyId), Protocol.GSON.toJson(update));
    }

    private void broadcastQueueEmpty(String partyId) {
        stateFor(partyId).processing = false;
        // Explicit phase (T5.4); clients also keep legacy queue_empty for compatibility.
        CombatPhaseCoordinator.broadcast(partyId, CombatPhase.QUEUE_EMPTY);

        if (!CrossSpireMod.isConnected()) return;

        Protocol.QueueEmptyMessage empty = new Protocol.QueueEmptyMessage();
        empty.source = CrossSpireMod.playerId;
        empty.seq = CrossSpireMod.nextSeq();
        empty.partyId = partyId(partyId);
        CrossSpireMod.sendToParty(partyId(partyId), Protocol.GSON.toJson(empty));

        CSLog.log("CentralQueueManager queue_empty broadcast");
    }

    private Protocol.QueueEntry toEntry(Protocol.QueueSubmitMessage pkt, String status) {
        Protocol.QueueEntry entry = new Protocol.QueueEntry();
        entry.packetId = pkt.packetId;
        entry.senderId = pkt.senderId;
        entry.ownerId = pkt.ownerId;
        entry.cardId = pkt.cardId;
        entry.target = pkt.gameTarget;
        entry.status = status;
        return entry;
    }

    private QueueState stateFor(String partyId) {
        String effective = partyId(partyId);
        synchronized (queues) {
            QueueState state = queues.get(effective);
            if (state == null) { state = new QueueState(); queues.put(effective, state); }
            return state;
        }
    }

    private static String partyId(String partyId) {
        return partyId == null || partyId.isEmpty() ? PartyManager.DEFAULT_PARTY_ID : partyId;
    }

    static final class CSLog {
        static void log(String msg) {
            System.out.println("[CrossSpire] " + msg);
        }
    }
}
