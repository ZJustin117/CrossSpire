package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
        pkt.packetId = pkt.senderId + "/" + UUID.randomUUID().toString().substring(0, 8);
        synchronized (queue) {
            for (Protocol.QueueSubmitMessage existing : queue) {
                if (existing.packetId != null && existing.packetId.equals(pkt.packetId)) return;
            }
            queue.add(pkt);
            Collections.sort(queue, ORDER);
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
        synchronized (queue) {
            for (int i = 0; i < queue.size(); i++) {
                if (packetId.equals(queue.get(i).packetId)) {
                    queue.remove(i);
                    break;
                }
            }
        }
        broadcastUpdate();
        if (queue.isEmpty()) {
            broadcastQueueEmpty();
        } else {
            processNext();
        }
    }

    private void processNext() {
        synchronized (queue) {
            if (queue.isEmpty()) { processing = false; return; }
            processing = true;
        }

        Protocol.QueueSubmitMessage head = queue.get(0);
        log("CentralQueueManager process: " + head.cardId + " owner=" + head.ownerId);

        if (head.ownerId != null && head.ownerId.equals(CrossSpireMod.playerId)) {
            handleOwnItem(head);
        } else {
            sendInvoke(head);
        }
    }

    private void handleOwnItem(Protocol.QueueSubmitMessage head) {
        log("CentralQueueManager own item: " + head.cardId);
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

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(invoke));
            log("CentralQueueManager sent invoke to " + head.ownerId.substring(0, 8)
                + " for " + head.cardId);
        }
    }

    public void onInvokeResult(Protocol.InvokeResultMessage result) {
        log("CentralQueueManager invoke_result: " + result.refId);

        Protocol.QueueComplete complete = new Protocol.QueueComplete();
        complete.source = CrossSpireMod.playerId;
        complete.seq = CrossSpireMod.nextSeq();
        complete.type = "combat_result";
        complete.packetId = result.refId;
        complete.effects = result.effects;
        complete.operationSequence = result.operationSequence;

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(complete));
        }

        String cardId = result.refId.contains("@") ? result.refId.split(":")[1].split("@")[0] : "unknown";
        synchronized (queue) {
            for (int i = 0; i < queue.size(); i++) {
                Protocol.QueueSubmitMessage q = queue.get(i);
                if (q.ownerId.equals(result.source) && q.cardId.equals(cardId)) {
                    q.packetId = result.refId;
                    markDone(result.refId);
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
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.QueueUpdateMessage update = new Protocol.QueueUpdateMessage();
        update.source = CrossSpireMod.playerId;
        update.seq = CrossSpireMod.nextSeq();
        update.entries = getEntries();
        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(update));
    }

    private void broadcastQueueEmpty() {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.QueueEmptyMessage empty = new Protocol.QueueEmptyMessage();
        empty.source = CrossSpireMod.playerId;
        empty.seq = CrossSpireMod.nextSeq();
        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(empty));

        processing = false;
        log("CentralQueueManager queue_empty broadcast");
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

    private static void log(String msg) {
        System.out.println("[CrossSpire] " + msg);
    }
}
