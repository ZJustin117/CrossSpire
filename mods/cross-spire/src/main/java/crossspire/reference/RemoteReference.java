package crossspire.reference;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteReference<T> extends Reference<T> {

    private static final Map<String, Protocol.InvokeResultMessage> pendingResults = new ConcurrentHashMap<String, Protocol.InvokeResultMessage>();
    private static final long TIMEOUT_MS = 30_000;

    private final boolean direct;

    public RemoteReference(String cardId, String ownerId, String resourceHash, boolean direct) {
        super("card:" + cardId + "@" + ownerId, ownerId, Type.REMOTE, resourceHash);
        this.direct = direct;
    }

    public static void onInvokeResult(Protocol.InvokeResultMessage result) {
        if (result.refId != null) {
            pendingResults.put(result.refId, result);
            BaseMod.logger.info("RemoteReference received invoke_result: " + result.refId);
        }
    }

    @Override
    public void dereference(Object... args) {
        String cardIdLocal = refId.split(":")[1].split("@")[0];
        String targetId = args.length > 0 ? (String) args[0] : "self";

        if (tryDegrade()) {
            BaseMod.logger.info("RemoteReference degraded, invoking locally: " + cardIdLocal);
            new LocalReference<Object>(cardIdLocal, CrossSpireMod.playerId).dereference(targetId);
            return;
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);

        BaseMod.logger.info("RemoteReference dereference: " + cardIdLocal + " on " + ownerId.substring(0, 8) + " direct=" + direct);

        Protocol.InvokeMessage invoke = new Protocol.InvokeMessage();
        invoke.source = CrossSpireMod.playerId;
        invoke.target = hostId != null && !hostId.isEmpty() ? hostId : ownerId;
        invoke.seq = CrossSpireMod.nextSeq();
        invoke.refId = refId + "/" + requestId;
        invoke.trigger = "play_card";
        invoke.args = targetId;

        String json = Protocol.GSON.toJson(invoke);

        if (direct) {
            CrossSpireMod.connectionManager.send(ownerId, json);
        } else {
            CrossSpireMod.send(json);
        }

        Protocol.InvokeResultMessage result = waitForResult(invoke.refId, TIMEOUT_MS);
        if (result != null) {
            BaseMod.logger.info("RemoteReference got result for " + cardIdLocal + ": " + result.effects.length + " effects");
            Protocol.QueueComplete complete = new Protocol.QueueComplete();
            complete.source = CrossSpireMod.playerId;
            complete.seq = CrossSpireMod.nextSeq();
            complete.packetId = invoke.refId;
            complete.effects = result.effects;
            complete.operationSequence = result.operationSequence;
            if (CrossSpireMod.isConnected()) {
                CrossSpireMod.send(Protocol.GSON.toJson(complete));
            }
        } else {
            BaseMod.logger.info("RemoteReference timeout for " + cardIdLocal + " on " + ownerId.substring(0, 8));
        }
    }

    private Protocol.InvokeResultMessage waitForResult(String refId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Protocol.InvokeResultMessage result = pendingResults.remove(refId);
            if (result != null) return result;
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        return null;
    }

    @Override
    public boolean tryDegrade() {
        String cardId = refId.split(":")[1].split("@")[0];
        if (ContentValidator.matches("card", cardId, resourceHash)) {
            BaseMod.logger.info("RemoteReference degraded to LOCAL: " + refId);
            return true;
        }
        return false;
    }
}
