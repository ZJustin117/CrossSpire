package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.events.AbstractEvent;
import crossspire.CrossSpireMod;
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;

public class EventSyncPatches {

    @SpirePatch(clz = AbstractEvent.class, method = "onEnterRoom", paramtypez = {})
    public static class OnEnterRoom {
        @SpirePostfixPatch
        public static void postfix(AbstractEvent __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            String eventName = __instance.getClass().getSimpleName();
            Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
            msg.source = CrossSpireMod.playerId;
            msg.seq = CrossSpireMod.nextSeq();
            msg.eventId = eventName;
            msg.effects = new Protocol.EffectDescription[0];
            CrossSpireMod.send(Protocol.GSON.toJson(msg));
            BaseMod.logger.info("EventSyncPatches onEnterRoom: " + eventName);
        }
    }

    @SpirePatch(clz = AbstractEvent.class, method = "enterCombat", paramtypez = {})
    public static class EnterCombat {
        @SpirePostfixPatch
        public static void postfix(AbstractEvent __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            String eventName = __instance.getClass().getSimpleName();
            Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
            msg.source = CrossSpireMod.playerId;
            msg.seq = CrossSpireMod.nextSeq();
            msg.eventId = eventName;
            msg.effects = new Protocol.EffectDescription[0];
            CrossSpireMod.send(Protocol.GSON.toJson(msg));
            BaseMod.logger.info("EventSyncPatches enterCombat: " + eventName);
        }
    }

    @SpirePatch(clz = AbstractEvent.class, method = "openMap", paramtypez = {})
    public static class OnOpenMap {
        @SpirePostfixPatch
        public static void postfix(AbstractEvent __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            String eventId = __instance.getClass().getSimpleName();
            String result = EventMessageSender.buildEventResult(eventId, 0, 0);
            CrossSpireMod.send(result);
            BaseMod.logger.info("EventSyncPatches event_result/openMap: " + eventId);
        }
    }
}
