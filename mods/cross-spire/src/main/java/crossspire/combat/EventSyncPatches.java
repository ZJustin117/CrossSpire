package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;
import java.lang.reflect.Field;

public class EventSyncPatches {

    @SpirePatch(clz = AbstractEvent.class, method = "onEnterRoom", paramtypez = {})
    public static class OnEnterRoom {
        @SpirePostfixPatch
        public static void postfix(AbstractEvent __instance) {
            if (CrossSpireMod.stageHost == null) return;
            if (!CrossSpireMod.stageHost.isStageHost() && !CrossSpireMod.isRoomHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            String eventId = __instance.getClass().getSimpleName();
            String eventClass = __instance.getClass().getName();

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
            } catch (Exception ignored) {}

            if (optionTexts.length > 0) {
                String msg = EventMessageSender.buildEventInterface(
                    eventId, eventClass, description, optionTexts, disabled);
                CrossSpireMod.send((String) msg);
                BaseMod.logger.info("EventSyncPatches event_interface: " + eventId
                    + " class=" + eventClass + " options=" + optionTexts.length);
            } else {
                Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
                msg.source = CrossSpireMod.playerId;
                msg.seq = CrossSpireMod.nextSeq();
                msg.eventId = eventId;
                msg.effects = new Protocol.EffectDescription[0];
                CrossSpireMod.send(Protocol.GSON.toJson(msg));
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
            CrossSpireMod.send((String) result);
            BaseMod.logger.info("EventSyncPatches event_result/openMap: " + eventId);
        }
    }

    @SpirePatch(clz = AbstractEvent.class, method = "buttonEffect", paramtypez = {int.class})
    public static class Sandbox {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractEvent __instance, int buttonPressed) {
            if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();
            if (CrossSpireMod.stageHost.isStageHost()) {
                BaseMod.logger.info("EventSandbox REAL mode: "
                    + __instance.getClass().getSimpleName() + " button=" + buttonPressed);
                return SpireReturn.Continue();
            }

            String eventId = __instance.getClass().getSimpleName();
            BaseMod.logger.info("EventSandbox start: " + eventId + " button=" + buttonPressed);

            int savedHp = 0;
            int savedBlock = 0;
            if (AbstractDungeon.player != null) {
                savedHp = AbstractDungeon.player.currentHealth;
                savedBlock = AbstractDungeon.player.currentBlock;
            }

            EventCapture.startTranscript(eventId);
            EventCapture.appendButtonEffect(buttonPressed);

            EventSuppression.suppressEvents(() -> {
                try {
                    java.lang.reflect.Method m = __instance.getClass()
                        .getDeclaredMethod("buttonEffect", int.class);
                    m.setAccessible(true);
                    m.invoke(__instance, buttonPressed);
                } catch (Exception e) {
                    BaseMod.logger.error("EventSandbox buttonEffect failed: " + e.getMessage());
                }
            });

            try {
                if (AbstractDungeon.gridSelectScreen != null
                    && AbstractDungeon.gridSelectScreen.selectedCards != null
                    && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
                    java.util.ArrayList<com.megacrit.cardcrawl.cards.AbstractCard> sel =
                        AbstractDungeon.gridSelectScreen.selectedCards;
                    String[] cardIds = new String[sel.size()];
                    for (int i = 0; i < sel.size(); i++) cardIds[i] = sel.get(i).cardID;
                    EventCapture.appendCardSelect(cardIds);
                    EventCapture.appendConfirm();
                    sel.clear();
                }
            } catch (Exception ignored) {}

            if (AbstractDungeon.player != null) {
                AbstractDungeon.player.currentHealth = savedHp;
                AbstractDungeon.player.currentBlock = savedBlock;
            }

            String transcript = EventCapture.buildTranscript();
            CrossSpireMod.send((String) transcript);
            BaseMod.logger.info("EventSandbox transcript: " + eventId
                + " button=" + buttonPressed);

            return SpireReturn.Return(null);
        }
    }
}
