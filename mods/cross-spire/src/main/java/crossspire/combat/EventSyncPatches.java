package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.events.AbstractEvent;
import crossspire.CrossSpireMod;
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;
import java.lang.reflect.Field;

public class EventSyncPatches {

    @SpirePatch(clz = AbstractEvent.class, method = "onEnterRoom", paramtypez = {})
    public static class OnEnterRoom {
        @SpirePostfixPatch
        public static void postfix(AbstractEvent __instance) {
            BaseMod.logger.info("EventSyncOnEnterRoom: " + __instance.getClass().getSimpleName()
                + " isRoomHost=" + CrossSpireMod.isRoomHost()
                + " isStageHost=" + (CrossSpireMod.stageHost != null && CrossSpireMod.stageHost.isStageHost())
                + " connected=" + CrossSpireMod.isConnected()
                + " localId=" + (CrossSpireMod.playerId.isEmpty() ? "?" : CrossSpireMod.playerId.substring(0, 8)));
            if (CrossSpireMod.stageHost == null) return;
            if (!CrossSpireMod.stageHost.isStageHost() && !CrossSpireMod.isRoomHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            String eventId = __instance.getClass().getSimpleName();

            String description = "";
            try {
                Field bodyField = AbstractEvent.class.getDeclaredField("body");
                bodyField.setAccessible(true);
                Object bodyVal = bodyField.get(__instance);
                if (bodyVal instanceof String) description = (String) bodyVal;
            } catch (Exception ignored) {}

            String[] optionTexts = new String[0];
            boolean[] disabled = new boolean[0];
            try {
                Field optionsField = __instance.getClass().getField("OPTIONS");
                optionTexts = (String[]) optionsField.get(null);
                disabled = new boolean[optionTexts.length];
                if (__instance.optionsSelected != null) {
                    for (int i = 0; i < optionTexts.length && i < __instance.optionsSelected.size(); i++) {
                        disabled[i] = __instance.optionsSelected.get(i) != null;
                    }
                }
            } catch (Exception e) {
                optionTexts = new String[0];
                disabled = new boolean[0];
            }

            if (optionTexts.length > 0) {
                String msg = EventMessageSender.buildEventInterface(
                    eventId, description, optionTexts, disabled);
                CrossSpireMod.send(msg);
                BaseMod.logger.info("EventSyncPatches event_interface: " + eventId
                    + " options=" + optionTexts.length);
            } else {
                Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
                msg.source = CrossSpireMod.playerId;
                msg.seq = CrossSpireMod.nextSeq();
                msg.eventId = eventId;
                msg.effects = new Protocol.EffectDescription[0];
                CrossSpireMod.send(Protocol.GSON.toJson(msg));
                BaseMod.logger.info("EventSyncPatches onEnterRoom: " + eventId + " (no options)");
            }
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
