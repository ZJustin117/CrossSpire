package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import crossspire.CrossSpireMod;
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;
import java.lang.reflect.Field;
import java.util.List;

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
                    + " options=" + optionTexts.length);
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
        }
    }

    /**
     * Selects a card by simulating a real player hover+click sequence.
     * Sets hoveredCard (via reflection) AND card.hb.clicked = true.
     * Engine processes this in its next updateCardPositionsAndHoverLogic().
     */
    public static boolean hoverSelectCard(String cardId) {
        if (AbstractDungeon.gridSelectScreen == null) return false;
        if (AbstractDungeon.gridSelectScreen.targetGroup == null) return false;

        for (com.megacrit.cardcrawl.cards.AbstractCard c : AbstractDungeon.gridSelectScreen.targetGroup.group) {
            if (c.cardID.equals(cardId)) {
                try {
                    java.lang.reflect.Field hf = com.megacrit.cardcrawl.screens.select.GridCardSelectScreen.class
                        .getDeclaredField("hoveredCard");
                    hf.setAccessible(true);
                    hf.set(AbstractDungeon.gridSelectScreen, c);
                    c.hb.clicked = true;
                    BaseMod.logger.info("EventSync hoverSelect: " + cardId);
                    return true;
                } catch (Exception e) {
                    BaseMod.logger.error("EventSync hoverSelect failed: " + e.getMessage());
                    return false;
                }
            }
        }
        BaseMod.logger.info("EventSync hoverSelect: card " + cardId + " not in pool");
        return false;
    }

    /**
     * Clicks the confirm button on the active GridCardSelectScreen.
     * Should be called after the engine has processed card clicks (next frame).
     */
    public static void clickConfirm() {
        if (AbstractDungeon.gridSelectScreen == null) return;
        if (AbstractDungeon.gridSelectScreen.confirmButton == null) return;
        AbstractDungeon.gridSelectScreen.confirmButton.hb.clicked = true;
        BaseMod.logger.info("EventSync clickConfirm");
    }
}
