package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.combat.CombatResultReplayer;
import crossspire.combat.QueueManager;
import crossspire.network.Protocol;
import crossspire.reference.RemoteReference;
import crossspire.ui.QueueDisplay;
import crossspire.resource.RemoteResourceManager;
import crossspire.resource.ResourceRegistryTracker;

public class MessageRouter {

    private final SyncExecutor syncExecutor;
    private final QueueManager queueManager;
    private final CombatResultReplayer resultReplayer;

    public MessageRouter(SyncExecutor syncExecutor, QueueManager queueManager, CombatResultReplayer resultReplayer) {
        this.syncExecutor = syncExecutor;
        this.queueManager = queueManager;
        this.resultReplayer = resultReplayer;
    }

    public void route(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String type = msg.get("type").getAsString();

        if ("connected".equals(type) || "room_state".equals(type) || "player_joined".equals(type)) {
            return;
        }

        String subtype = msg.has("subtype") ? msg.get("subtype").getAsString() : "";

        if ("queue_packet".equals(type)) {
            BaseMod.logger.info("MessageRouter queue_packet routing...");
            try {
                Protocol.QueuePacket pkt = Protocol.GSON.fromJson(rawMessage, Protocol.QueuePacket.class);
                BaseMod.logger.info("MessageRouter queue_packet parsed: " + pkt.cardId + " by " + pkt.senderId.substring(0,8));
                queueManager.onQueuePacket(pkt);
                QueueDisplay.onPacket(pkt);
            } catch (Exception e) {
                BaseMod.logger.error("MessageRouter queue_packet parse error: " + e.getMessage());
            }
        } else if ("queue_complete".equals(type)) {
            Protocol.QueueComplete complete = Protocol.GSON.fromJson(rawMessage, Protocol.QueueComplete.class);
            queueManager.onQueueComplete(complete);
            QueueDisplay.onComplete(complete.packetId);
            resultReplayer.handleCombatResult(rawMessage);
        } else if ("player_state".equals(type)) {
            syncExecutor.handleSync("remote_player", null, 1, rawMessage);
        } else if ("stage_sync".equals(type)) {
            syncExecutor.handleSync(subtype.isEmpty() ? "battle_start" : subtype, null, 1, rawMessage);
        } else if ("state_sync".equals(type)) {
            syncExecutor.handleSync(subtype, null, 1, rawMessage);
        } else if ("resource_registry".equals(type)) {
            ResourceRegistryTracker.onRegistryReceived(rawMessage);
        } else if ("resource_request".equals(type)) {
            BaseMod.logger.info("MessageRouter resource_request ignored");
        } else if ("resource_response".equals(type)) {
            RemoteResourceManager.onResourceResponse(rawMessage);
        } else if ("hello".equals(type)) {
            CrossSpireMod.p2pManager.onHelloReceived(rawMessage);
        } else if ("player_ready".equals(type)) {
            CrossSpireMod.lobbyState.onPlayerReady(rawMessage);
        } else if ("invoke".equals(type)) {
            handleInvoke(rawMessage);
        } else if ("invoke_result".equals(type)) {
            Protocol.InvokeResultMessage result = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeResultMessage.class);
            RemoteReference.onInvokeResult(result);
        }
    }

    private void handleInvoke(String rawMessage) {
        Protocol.InvokeMessage inv = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeMessage.class);
        BaseMod.logger.info("MessageRouter invoke: " + inv.refId + " trigger=" + inv.trigger);
        crossspire.reference.Reference<Object> ref = new crossspire.reference.LocalReference<Object>(
            inv.refId.contains("@") ? inv.refId.split(":")[1].split("@")[0] : "unknown",
            CrossSpireMod.playerId
        );
        ref.dereference(inv.args);

        Protocol.EffectDescription dmg = new Protocol.EffectDescription();
        dmg.kind = "damage";
        dmg.target = "self";
        dmg.amount = 6;

        Protocol.InvokeResultMessage result = new Protocol.InvokeResultMessage();
        result.source = CrossSpireMod.playerId;
        result.seq = 1;
        result.refId = inv.refId;
        result.effects = new Protocol.EffectDescription[] { dmg };
        result.operationSequence = new Protocol.OperationStep[0];

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
            BaseMod.logger.info("MessageRouter sent invoke_result: " + inv.refId);
        }
    }
}
