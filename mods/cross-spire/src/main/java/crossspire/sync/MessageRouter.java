package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.combat.QueueManager;
import crossspire.network.Protocol;
import crossspire.resource.RemoteResourceManager;
import crossspire.resource.ResourceRegistryTracker;

public class MessageRouter {

    private final SyncExecutor syncExecutor;
    private final QueueManager queueManager;

    public MessageRouter(SyncExecutor syncExecutor, QueueManager queueManager) {
        this.syncExecutor = syncExecutor;
        this.queueManager = queueManager;
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
            } catch (Exception e) {
                BaseMod.logger.error("MessageRouter queue_packet parse error: " + e.getMessage());
            }
        } else if ("queue_complete".equals(type)) {
            Protocol.QueueComplete complete = Protocol.GSON.fromJson(rawMessage, Protocol.QueueComplete.class);
            queueManager.onQueueComplete(complete);
            syncExecutor.handleSync("combat_result", null, 1, rawMessage);
        } else if ("player_state".equals(type)) {
            syncExecutor.handleSync("remote_player", null, 1, rawMessage);
        } else if ("stage_sync".equals(type)) {
            syncExecutor.handleSync("battle_start", null, 1, rawMessage);
        } else if ("state_sync".equals(type)) {
            syncExecutor.handleSync(subtype, null, 1, rawMessage);
        } else if ("resource_registry".equals(type)) {
            ResourceRegistryTracker.onRegistryReceived(rawMessage);
        } else if ("resource_request".equals(type)) {
            BaseMod.logger.info("MessageRouter resource_request ignored");
        } else if ("resource_response".equals(type)) {
            RemoteResourceManager.onResourceResponse(rawMessage);
        }
    }
}
