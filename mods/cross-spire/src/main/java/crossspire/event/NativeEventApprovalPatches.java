package crossspire.event;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.network.StandardPacket;
import crossspire.reference.ContentValidator;
import java.util.ArrayList;
import java.util.List;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/** Gates the shared native event button dispatch until RoomHost approval arrives. */
public final class NativeEventApprovalPatches {

    private static final NativeEventApprovalGate GATE = new NativeEventApprovalGate();
    private static final ThreadLocal<PersonalPlayerSnapshot> BEFORE =
        new ThreadLocal<PersonalPlayerSnapshot>();

    private NativeEventApprovalPatches() {}

    public static boolean bind(AbstractEvent event, Protocol.EventInterfacePayload iface) {
        boolean ok = GATE.bind(event, iface);
        if (ok && iface != null) {
            EventOpenModeRegistry.mark(iface.eventInstanceId, EventOpenModeRegistry.NATIVE);
        }
        return ok;
    }

    public static boolean bindIfMatching(AbstractEvent event, Protocol.EventInterfacePayload iface) {
        if (event == null || iface == null || iface.eventClass == null
            || !iface.eventClass.equals(event.getClass().getName())) {
            return false;
        }
        String localHash = ContentValidator.hashClass(iface.eventClass);
        if (localHash.isEmpty() || !localHash.equals(iface.resourceHash)) return false;
        return bind(event, iface);
    }

    public static boolean intercept(AbstractEvent event, int optionIndex) {
        NativeEventApprovalGate.Attempt attempt = GATE.beforeButtonEffect(event, optionIndex);
        if (attempt.execute) return true;
        RoomEventDialog.waitForInput = true;
        if (attempt.request != null) send(attempt.request);
        return false;
    }

    /** Snapshot personal state immediately before a permitted native buttonEffect. */
    public static void beforeExecute(AbstractEvent event) {
        BEFORE.set(captureLocal());
    }

    /** Diff personal state after permitted execute and emit event_player_result. */
    public static void afterExecute(AbstractEvent event) {
        PersonalPlayerSnapshot before = BEFORE.get();
        BEFORE.remove();
        Protocol.EventChoiceDecisionPayload decision = GATE.consumeLastApproved(event);
        if (before == null || decision == null || CrossSpireMod.playerId == null
            || CrossSpireMod.playerId.isEmpty()) {
            return;
        }
        PersonalPlayerSnapshot after = captureLocal();
        Protocol.EventPlayerResultPayload result = PersonalEventDeltaPlanner.toResult(
            decision, CrossSpireMod.playerId, before, after);
        if (result == null) return;
        StandardPacket packet = EventChoiceSender.resultPacket(result);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        BaseMod.logger.info("NativeEventApproval personal result event=" + result.eventInstanceId
            + " request=" + result.requestId
            + " effects=" + (result.effects != null ? result.effects.length : 0));
    }

    public static void approved(Protocol.EventChoiceDecisionPayload decision) {
        if (GATE.approve(decision)) {
            RoomEventDialog.waitForInput = false;
            return;
        }
        // Console diagnostics send event_choice_request without going through intercept();
        // arm the bound native event from the approved decision and force one buttonEffect.
        if (decision == null) return;
        Object event = GATE.findEventForDecision(decision);
        if (!(event instanceof AbstractEvent)) return;
        Protocol.EventChoiceRequestPayload synthetic = new Protocol.EventChoiceRequestPayload();
        synthetic.eventInstanceId = decision.eventInstanceId;
        synthetic.partyId = decision.partyId;
        synthetic.requestId = decision.requestId;
        synthetic.uiStep = decision.uiStep != null ? decision.uiStep : NativeEventApprovalGate.STEP_BUTTON;
        synthetic.optionIndex = decision.optionIndex;
        if (!GATE.armApprovedRequest(event, synthetic, decision)) return;
        RoomEventDialog.waitForInput = false;
        forceButtonEffect((AbstractEvent) event, decision.optionIndex);
    }

    public static void rejected(Protocol.EventChoiceDecisionPayload decision) {
        if (GATE.reject(decision)) {
            RoomEventDialog.waitForInput = true;
        }
    }

    private static void forceButtonEffect(final AbstractEvent event, final int optionIndex) {
        try {
            com.badlogic.gdx.Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    try {
                        beforeExecute(event);
                        java.lang.reflect.Method m = AbstractEvent.class.getDeclaredMethod(
                            "buttonEffect", int.class);
                        m.setAccessible(true);
                        m.invoke(event, Integer.valueOf(optionIndex));
                        afterExecute(event);
                    } catch (Exception e) {
                        BaseMod.logger.error("NativeEventApproval forced buttonEffect failed: "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            BaseMod.logger.error("NativeEventApproval schedule buttonEffect failed: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static PersonalPlayerSnapshot captureLocal() {
        if (AbstractDungeon.player == null) {
            return new PersonalPlayerSnapshot(0, 0, 0, 0, 0, null, null, null);
        }
        int deck = AbstractDungeon.player.masterDeck != null
            ? AbstractDungeon.player.masterDeck.size() : 0;
        List<String> relics = new ArrayList<String>();
        if (AbstractDungeon.player.relics != null) {
            for (com.megacrit.cardcrawl.relics.AbstractRelic r : AbstractDungeon.player.relics) {
                if (r != null && r.relicId != null) relics.add(r.relicId);
            }
        }
        List<String> potions = new ArrayList<String>();
        if (AbstractDungeon.player.potions != null) {
            for (com.megacrit.cardcrawl.potions.AbstractPotion p : AbstractDungeon.player.potions) {
                if (p != null && p.ID != null
                    && !"Potion Slot".equals(p.ID) && !"PotionSlot".equals(p.ID)) {
                    potions.add(p.ID);
                }
            }
        }
        List<String> cards = new ArrayList<String>();
        if (AbstractDungeon.player.masterDeck != null
            && AbstractDungeon.player.masterDeck.group != null) {
            for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
                if (c != null && c.cardID != null) cards.add(c.cardID);
            }
        }
        return new PersonalPlayerSnapshot(
            AbstractDungeon.player.gold,
            AbstractDungeon.player.currentHealth,
            AbstractDungeon.player.maxHealth,
            AbstractDungeon.player.currentBlock,
            deck,
            relics.toArray(new String[0]),
            potions.toArray(new String[0]),
            cards.toArray(new String[0]));
    }

    private static void send(Protocol.EventChoiceRequestPayload request) {
        StandardPacket packet = EventChoiceSender.requestPacket(request);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        BaseMod.logger.info("NativeEventApproval request event=" + request.eventInstanceId
            + " request=" + request.requestId);
    }

    @SpirePatch(clz = AbstractEvent.class, method = "update", paramtypez = {})
    public static class ButtonEffectDispatch {
        @SpireInstrumentPatch
        public static ExprEditor instrument() {
            return new ExprEditor() {
                @Override
                public void edit(MethodCall call) throws CannotCompileException {
                    if ("buttonEffect".equals(call.getMethodName())
                        && "(I)V".equals(call.getSignature())) {
                        call.replace(
                            "{ if (crossspire.event.NativeEventApprovalPatches.intercept($0, $1)) {"
                                + " crossspire.event.NativeEventApprovalPatches.beforeExecute($0);"
                                + " $proceed($$);"
                                + " crossspire.event.NativeEventApprovalPatches.afterExecute($0);"
                                + " } }");
                    }
                }
            };
        }
    }

    /**
     * When a bound native event opened a grid select screen, confirm requires a cardSelect
     * approval step before the engine consumes the click.
     */
    @SpirePatch(clz = GridCardSelectScreen.class, method = "update", paramtypez = {})
    public static class GateGridConfirm {
        @SpirePrefixPatch
        public static void Prefix(GridCardSelectScreen __instance) {
            if (__instance == null || __instance.confirmButton == null
                || __instance.confirmButton.hb == null
                || !__instance.confirmButton.hb.clicked) {
                return;
            }
            AbstractEvent event = AbstractDungeon.getCurrRoom() != null
                ? AbstractDungeon.getCurrRoom().event : null;
            if (event == null || !GATE.isBound(event)) return;
            List<String> ids = new ArrayList<String>();
            if (__instance.selectedCards != null) {
                for (AbstractCard card : __instance.selectedCards) {
                    if (card != null && card.cardID != null) ids.add(card.cardID);
                }
            }
            if (!GridCardSelectApprovalPlanner.shouldGate(true, true, ids.size())) return;
            String[] cardIds = GridCardSelectApprovalPlanner.cardIds(ids);
            NativeEventApprovalGate.Attempt attempt = GATE.beforeChoice(
                event, NativeEventApprovalGate.STEP_CARD_SELECT, GATE.lastOptionIndex(event), cardIds);
            if (attempt.execute) return;
            __instance.confirmButton.hb.clicked = false;
            if (attempt.request != null) send(attempt.request);
            BaseMod.logger.info("NativeEventApproval grid confirm gated event="
                + (GATE.interfaceFor(event) != null
                    ? GATE.interfaceFor(event).eventInstanceId : "?")
                + " cards=" + cardIds.length);
        }
    }

    /**
     * When a bound native event opened a hand select screen, confirm requires a targetSelect
     * approval step (selected card IDs as targets) before the engine consumes the click.
     */
    @SpirePatch(clz = HandCardSelectScreen.class, method = "update", paramtypez = {})
    public static class GateHandConfirm {
        @SpirePrefixPatch
        public static void Prefix(HandCardSelectScreen __instance) {
            if (__instance == null || __instance.button == null
                || __instance.button.hb == null
                || !__instance.button.hb.clicked) {
                return;
            }
            AbstractEvent event = AbstractDungeon.getCurrRoom() != null
                ? AbstractDungeon.getCurrRoom().event : null;
            if (event == null || !GATE.isBound(event)) return;
            List<String> ids = new ArrayList<String>();
            if (__instance.selectedCards != null && __instance.selectedCards.group != null) {
                for (AbstractCard card : __instance.selectedCards.group) {
                    if (card != null && card.cardID != null) ids.add(card.cardID);
                }
            }
            if (!TargetSelectApprovalPlanner.shouldGate(true, true, ids.size())) return;
            String[] targetIds = TargetSelectApprovalPlanner.targetIds(ids);
            NativeEventApprovalGate.Attempt attempt = GATE.beforeChoice(
                event, NativeEventApprovalGate.STEP_TARGET_SELECT, GATE.lastOptionIndex(event),
                null, targetIds);
            if (attempt.execute) return;
            __instance.button.hb.clicked = false;
            if (attempt.request != null) send(attempt.request);
            BaseMod.logger.info("NativeEventApproval hand confirm gated event="
                + (GATE.interfaceFor(event) != null
                    ? GATE.interfaceFor(event).eventInstanceId : "?")
                + " targets=" + targetIds.length);
        }
    }
}
