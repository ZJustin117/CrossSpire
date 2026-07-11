package crossspire.sync;

import basemod.BaseMod;
import com.google.gson.JsonObject;
import crossspire.network.Protocol;
import crossspire.remote.StageHost;
import crossspire.resource.RemoteResourceManager;
import crossspire.resource.ResourceRegistryTracker;

public class MessageRouter {

    private final InvokeExecutor invokeExecutor;
    private final SyncExecutor syncExecutor;
    private final StageHost stageHost;

    public MessageRouter(InvokeExecutor invokeExecutor, SyncExecutor syncExecutor, StageHost stageHost) {
        this.invokeExecutor = invokeExecutor;
        this.syncExecutor = syncExecutor;
        this.stageHost = stageHost;
    }

    public void route(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String type = msg.get("type").getAsString();

        if ("connected".equals(type) || "room_state".equals(type) || "player_joined".equals(type)) {
            return;
        }

        String subtype = msg.has("subtype") ? msg.get("subtype").getAsString() : "";
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String target = msg.has("target") ? msg.get("target").getAsString() : "";
        int seq = msg.has("seq") ? msg.get("seq").getAsInt() : -1;

        if ("queue_submit".equals(type)) {
            BaseMod.logger.info("MessageRouter queue_submit");
            Protocol.QueueSubmit submit = Protocol.GSON.fromJson(rawMessage, Protocol.QueueSubmit.class);
            stageHost.onQueueSubmit(submit, rawMessage);
        } else if ("invoke_card".equals(type)) {
            BaseMod.logger.info("MessageRouter invoke_card target=" + target);
            invokeExecutor.handleInvoke(type, source, target, seq, rawMessage);
        } else if ("invoke_result".equals(type)) {
            BaseMod.logger.info("MessageRouter invoke_result");
            Protocol.InvokeResult result = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeResult.class);
            stageHost.onInvokeResult(result);
        } else if ("state_sync".equals(type)) {
            BaseMod.logger.info("MessageRouter state_sync " + subtype);
            syncExecutor.handleSync(subtype, source, seq, rawMessage);
        } else if ("resource_registry".equals(type)) {
            ResourceRegistryTracker.onRegistryReceived(rawMessage);
        } else if ("resource_request".equals(type)) {
            BaseMod.logger.info("MessageRouter resource_request ignored (MVP)");
        } else if ("resource_response".equals(type)) {
            RemoteResourceManager.onResourceResponse(rawMessage);
        }
    }
}
