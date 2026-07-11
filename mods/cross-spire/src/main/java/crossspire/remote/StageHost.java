package crossspire.remote;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.sync.SyncExecutor;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class StageHost {

    private final Queue<Protocol.QueueEntry> pendingQueue = new LinkedList<Protocol.QueueEntry>();
    private final SyncExecutor syncExecutor = new SyncExecutor();
    private String currentRequestId = null;
    private boolean processing = false;

    public void onQueueSubmit(Protocol.QueueSubmit submit, String json) {
        if (!crossspire.ui.ServerPicker.isStageHost) {
            return;
        }
        Protocol.QueueEntry entry = new Protocol.QueueEntry();
        entry.entryId = UUID.randomUUID().toString().substring(0, 8);
        entry.source = submit.source;
        entry.cardId = submit.cardId;
        entry.upgraded = submit.upgraded;
        entry.target = submit.target;
        entry.status = "pending";

        pendingQueue.add(entry);
        BaseMod.logger.info("StageHost queue added: " + entry.cardId + " from " + entry.source + " queue_size=" + pendingQueue.size());
        broadcastQueueUpdate();
        maybeProcessNext();
    }

    public void onInvokeResult(Protocol.InvokeResult result) {
        if (!processing || currentRequestId == null || !currentRequestId.equals(result.requestId)) return;

        BaseMod.logger.info("StageHost invoke_result: " + result.requestId);
        processing = false;
        currentRequestId = null;

        Protocol.QueueEntry entry = pendingQueue.poll();
        String cardId = entry != null ? entry.cardId : "";
        if (entry != null) {
            BaseMod.logger.info("StageHost queue done: " + entry.cardId + " remaining=" + pendingQueue.size());
        }

        JsonObject syncMsg = buildCombatSync(result, cardId);
        String syncJson = syncMsg.toString();

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(syncJson);
        }
        syncExecutor.handleSync("combat_result", "stage_host", 1, syncJson);
        broadcastQueueUpdate();
        maybeProcessNext();
    }

    private void maybeProcessNext() {
        if (processing || pendingQueue.isEmpty()) return;
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.QueueEntry entry = pendingQueue.peek();
        if (entry == null || !"pending".equals(entry.status)) return;

        entry.status = "executing";
        processing = true;
        currentRequestId = UUID.randomUUID().toString().substring(0, 8);
        broadcastQueueUpdate();

        Protocol.InvokeCard inv = new Protocol.InvokeCard();
        inv.source = "stage_host";
        inv.target = entry.source; // card owner's playerId
        inv.seq = 1;
        inv.requestId = currentRequestId;
        inv.cardId = entry.cardId;
        inv.upgraded = entry.upgraded;
        inv.gameTarget = entry.target; // game target (monster_0, self, etc)

        BaseMod.logger.info("StageHost invoke_card: " + inv.cardId + "→" + entry.source
            + " target=" + entry.target + " request=" + currentRequestId);

        if (entry.source.equals(CrossSpireMod.playerId)) {
            BaseMod.logger.info("StageHost local invoke (card owner is self)");
            String json = Protocol.GSON.toJson(inv);

            // simulate invoke result directly
            Protocol.EffectDescription dmg = new Protocol.EffectDescription();
            dmg.kind = "damage";
            dmg.target = entry.target;
            dmg.amount = 6;

            Protocol.InvokeResult result = new Protocol.InvokeResult();
            result.source = CrossSpireMod.playerId;
            result.target = "stage_host";
            result.seq = 1;
            result.requestId = inv.requestId;
            result.effects = new Protocol.EffectDescription[] { dmg };
            result.operationSequence = new Protocol.OperationStep[0];

            onInvokeResult(result);
        } else {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(inv));
        }
    }

    private void broadcastQueueUpdate() {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.QueueUpdate update = new Protocol.QueueUpdate();
        update.source = "stage_host";
        update.seq = 1;
        update.entries = pendingQueue.toArray(new Protocol.QueueEntry[0]);

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(update));
    }

    private JsonObject buildCombatSync(Protocol.InvokeResult result, String cardId) {
        JsonObject syncMsg = new JsonObject();
        syncMsg.addProperty("type", "state_sync");
        syncMsg.addProperty("subtype", "combat_result");
        syncMsg.addProperty("source", "stage_host");
        syncMsg.addProperty("seq", 1);
        syncMsg.addProperty("request_id", result.requestId);
        syncMsg.addProperty("card_id", cardId);
        syncMsg.add("effects", Protocol.GSON.toJsonTree(result.effects));
        syncMsg.add("operation_sequence", Protocol.GSON.toJsonTree(result.operationSequence));
        return syncMsg;
    }

    public int queueSize() {
        return pendingQueue.size();
    }
}
