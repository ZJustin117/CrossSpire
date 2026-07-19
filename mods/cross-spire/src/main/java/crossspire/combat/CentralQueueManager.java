package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CentralQueueManager {

    private final List<Protocol.QueueSubmitMessage> queue = Collections.synchronizedList(new ArrayList<Protocol.QueueSubmitMessage>());
    private boolean processing = false;

    private static final Comparator<Protocol.QueueSubmitMessage> ORDER = new Comparator<Protocol.QueueSubmitMessage>() {
        @Override
        public int compare(Protocol.QueueSubmitMessage a, Protocol.QueueSubmitMessage b) {
            if (a.timestamp != b.timestamp) return Long.compare(a.timestamp, b.timestamp);
            return a.senderId.compareTo(b.senderId);
        }
    };

    public void onQueueSubmit(Protocol.QueueSubmitMessage pkt) {
        String dedupKey = pkt.senderId + "/" + pkt.seq;
        pkt.packetId = dedupKey;
        boolean becameNonEmpty;
        synchronized (queue) {
            for (Protocol.QueueSubmitMessage existing : queue) {
                if (existing.packetId != null && existing.packetId.equals(dedupKey)) return;
            }
            becameNonEmpty = queue.isEmpty();
            queue.add(pkt);
            Collections.sort(queue, ORDER);
        }
        CSLog.log("CentralQueueManager added: " + pkt.cardId + " size=" + queue.size());
        if (becameNonEmpty) {
            CombatPhaseCoordinator.broadcast(CombatPhase.RESOLVING_QUEUE);
        }
        broadcastUpdate();
        if (!processing) processNext();
    }

    public Protocol.QueueSubmitMessage dequeue() {
        synchronized (queue) {
            if (queue.isEmpty()) return null;
            return queue.remove(0);
        }
    }

    public int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public Protocol.QueueEntry[] getEntries() {
        synchronized (queue) {
            Protocol.QueueEntry[] entries = new Protocol.QueueEntry[queue.size()];
            for (int i = 0; i < queue.size(); i++) {
                entries[i] = toEntry(queue.get(i), i == 0 && processing ? "executing" : "pending");
            }
            return entries;
        }
    }

    public void setExecuting(String cardId) {
        broadcastUpdate();
    }

    public void markDone(String packetId) {
        boolean wasEmpty;
        synchronized (queue) {
            for (int i = 0; i < queue.size(); i++) {
                if (packetId.equals(queue.get(i).packetId)) {
                    queue.remove(i);
                    break;
                }
            }
            wasEmpty = queue.isEmpty();
        }
        broadcastUpdate();
        if (wasEmpty) {
            broadcastQueueEmpty();
        } else {
            processNext();
        }
    }

    private void processNext() {
        Protocol.QueueSubmitMessage head;
        synchronized (queue) {
            if (queue.isEmpty()) { processing = false; return; }
            processing = true;
            head = queue.get(0);
        }

        CSLog.log("CentralQueueManager process: " + head.cardId + " owner=" + head.ownerId);

        if (head.ownerId != null && head.ownerId.equals(CrossSpireMod.playerId)) {
            handleOwnItem(head);
        } else {
            sendInvoke(head);
        }
    }

    private void handleOwnItem(Protocol.QueueSubmitMessage head) {
        CSLog.log("CentralQueueManager own item: " + head.cardId + " target=" + head.gameTarget);
        crossspire.sync.LocalCapturePatches.pushSuppress();
        crossspire.reference.LocalReference<Object> ref = new crossspire.reference.LocalReference<Object>(head.cardId, CrossSpireMod.playerId);
        ref.dereference(head.gameTarget);

        if (head.packetId != null) markDone(head.packetId);
    }

    private void sendInvoke(Protocol.QueueSubmitMessage head) {
        Protocol.InvokeMessage invoke = new Protocol.InvokeMessage();
        invoke.source = CrossSpireMod.playerId;
        invoke.target = head.ownerId;
        invoke.seq = CrossSpireMod.nextSeq();
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
        complete.packetId = result.refId;
        complete.effects = result.effects;
        complete.operationSequence = result.operationSequence;

        if (CrossSpireMod.isConnected()) {
            CrossSpireMod.send(Protocol.GSON.toJson(complete));
        }

        String cardId = result.refId.contains("@") ? result.refId.split(":")[1].split("@")[0] : "unknown";
        synchronized (queue) {
            for (Protocol.QueueSubmitMessage q : queue) {
                if (q.ownerId.equals(result.source) && q.cardId.equals(cardId)) {
                    markDone(q.packetId);
                    return;
                }
            }
        }
    }

    public void onCombatResultReplayed(String sourceCardId) {
        processing = false;
        processNext();
    }

    private void broadcastUpdate() {
        if (!CrossSpireMod.isConnected()) return;

        Protocol.QueueUpdateMessage update = new Protocol.QueueUpdateMessage();
        update.source = CrossSpireMod.playerId;
        update.seq = CrossSpireMod.nextSeq();
        update.entries = getEntries();
        CrossSpireMod.send(Protocol.GSON.toJson(update));
    }

    private void broadcastQueueEmpty() {
        processing = false;
        // Explicit phase (T5.4); clients also keep legacy queue_empty for compatibility.
        CombatPhaseCoordinator.broadcast(CombatPhase.QUEUE_EMPTY);

        if (!CrossSpireMod.isConnected()) return;

        Protocol.QueueEmptyMessage empty = new Protocol.QueueEmptyMessage();
        empty.source = CrossSpireMod.playerId;
        empty.seq = CrossSpireMod.nextSeq();
        CrossSpireMod.send(Protocol.GSON.toJson(empty));

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

    static final class CSLog {
        static void log(String msg) {
            System.out.println("[CrossSpire] " + msg);
        }
    }
}
