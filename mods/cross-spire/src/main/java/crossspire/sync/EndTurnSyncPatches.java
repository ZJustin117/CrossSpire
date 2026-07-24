package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import crossspire.CrossSpireMod;
import crossspire.combat.CombatPhase;
import crossspire.combat.CombatPhaseCoordinator;
import crossspire.network.Protocol;
import crossspire.party.PartyCoordinator;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import crossspire.ui.QueueDisplay;

/**
 * T8.0: multiplayer end-turn is announced only when the player actually ends the turn
 * ({@code EndTurnButton.disable(true)}), not when the button is merely enabled for the player phase.
 */
public final class EndTurnSyncPatches {

    public static boolean suppressEndTurn = false;

    private EndTurnSyncPatches() {}

    /** Block enabling end-turn while the party queue still has entries. */
    @SpirePatch(clz = EndTurnButton.class, method = "enable", paramtypez = {})
    public static class GateEnable {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(EndTurnButton __instance) {
            if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();
            if (!QueueDisplay.isEndTurnAllowed() && QueueDisplay.size() > 0) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Real end-turn path: STS sets {@code player.endTurnQueued} only when {@code disable(true)}.
     * Do not broadcast from {@code enable()} — that fires at the start of every player_turn.
     */
    @SpirePatch(clz = EndTurnButton.class, method = "disable", paramtypez = {boolean.class})
    public static class CaptureDisable {
        @SpirePostfixPatch
        public static void postfix(EndTurnButton __instance, boolean isEnemyTurn) {
            if (!shouldBroadcastEndTurn(isEnemyTurn, suppressEndTurn)) return;
            if (!CrossSpireMod.isConnected()) return;
            broadcastPlayerEndTurn();
        }
    }

    /** Pure policy: STS ends the player turn only via {@code disable(true)}. */
    public static boolean isMultiplayerEndTurnSignal(boolean isEnemyTurn) {
        return isEnemyTurn;
    }

    public static boolean shouldBroadcastEndTurn(boolean isEnemyTurn, boolean suppressed) {
        return isMultiplayerEndTurnSignal(isEnemyTurn) && !suppressed;
    }

    /** Shared so console/tests can invoke the wire path without UI. */
    public static void broadcastPlayerEndTurn() {
        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.partyId = localPartyId();
        String endTurn = Protocol.GSON.toJson(msg);
        boolean localLeader = PartyCoordinator.isLeader(
            CrossSpireMod.partyManager, msg.partyId, CrossSpireMod.playerId);
        if (CrossSpireMod.isRoomHost() && !localLeader && CrossSpireMod.messageRouter != null) {
            // Host has no loopback; route readiness through the normal host path to the leader.
            CrossSpireMod.messageRouter.route(endTurn);
        } else if (!CrossSpireMod.isRoomHost()) {
            CrossSpireMod.send(endTurn);
        }
        if (localLeader && CrossSpireMod.partyEndTurnTracker != null) {
            PartyState party = CrossSpireMod.partyManager.getParty(msg.partyId);
            if (CrossSpireMod.partyEndTurnTracker.markReady(party, CrossSpireMod.playerId)) {
                CrossSpireMod.partyEndTurnTracker.clear(msg.partyId);
                CombatPhaseCoordinator.broadcast(msg.partyId, CombatPhase.PRE_MONSTER_TURN);
                CombatPhaseCoordinator.broadcast(msg.partyId, CombatPhase.MONSTER_TURN);
            }
        }
        BaseMod.logger.info("EndTurnSync broadcast player_end_turn (disable true)");
    }

    private static String localPartyId() {
        if (CrossSpireMod.partyManager == null) return PartyManager.DEFAULT_PARTY_ID;
        String partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        return partyId != null ? partyId : PartyManager.DEFAULT_PARTY_ID;
    }
}
