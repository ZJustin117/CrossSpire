package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.events.AbstractEvent;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;

@SpirePatch(clz = AbstractEvent.class, method = "buttonEffect", paramtypez = {int.class}, optional = true)
public class EventSyncPatches {

    @SpirePrefixPatch
    public static void prefix(AbstractEvent __instance) {
        if (CrossSpireMod.stageHost == null) return;
        if (!CrossSpireMod.stageHost.isStageHost()) {
            EventSuppression.SUPPRESSION.incrementAndGet();
        }
    }

    @SpirePostfixPatch
    public static void postfix(AbstractEvent __instance) {
        if (CrossSpireMod.stageHost == null) return;
        if (!CrossSpireMod.stageHost.isStageHost()) {
            EventSuppression.SUPPRESSION.decrementAndGet();
            return;
        }

        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            String eventName = __instance.getClass().getSimpleName();
            Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
            msg.source = CrossSpireMod.playerId;
            msg.seq = (int) (System.currentTimeMillis() % 100000);
            msg.eventId = eventName;
            msg.effects = new Protocol.EffectDescription[0];
            CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
            BaseMod.logger.info("EventSyncPatches broadcast event_result: " + eventName);
        }
    }
}
