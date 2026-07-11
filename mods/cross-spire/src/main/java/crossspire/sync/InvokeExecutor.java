package crossspire.sync;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import com.google.gson.JsonObject;

public class InvokeExecutor {

    public void handleInvoke(String type, String source, String target, int seq, String rawMessage) {
        if ("invoke_card".equals(type)) {
            if (target != null && !target.isEmpty() && !target.equals(CrossSpireMod.playerId)) {
                BaseMod.logger.info("InvokeExecutor ignoring invoke_card for " + target + " (I am " + CrossSpireMod.playerId.substring(0,8) + ")");
                return;
            }
            Protocol.InvokeCard inv = Protocol.GSON.fromJson(rawMessage, Protocol.InvokeCard.class);
            BaseMod.logger.info("InvokeExecutor invoke_card card=" + inv.cardId + " request=" + inv.requestId);

            Protocol.EffectDescription dmg = new Protocol.EffectDescription();
            dmg.kind = "damage";
            dmg.target = inv.target;
            dmg.amount = 6;

            Protocol.InvokeResult result = new Protocol.InvokeResult();
            result.source = inv.target;
            result.seq = seq;
            result.requestId = inv.requestId;
            result.effects = new Protocol.EffectDescription[] { dmg };
            result.operationSequence = new Protocol.OperationStep[0];

            if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
                CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
                BaseMod.logger.info("InvokeExecutor sent invoke_result for " + inv.requestId);
            }
        }
    }
}
