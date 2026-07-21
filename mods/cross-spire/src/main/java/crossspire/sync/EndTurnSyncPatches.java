package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyCoordinator;
import crossspire.party.PartyState;
import crossspire.ui.QueueDisplay;

@SpirePatch(clz = EndTurnButton.class, method = "enable", paramtypez = {})
public class EndTurnSyncPatches {

    public static boolean suppressEndTurn = false;

    @SpirePrefixPatch
    public static SpireReturn<Void> prefix(EndTurnButton __instance) {
        if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();
        if (!QueueDisplay.isEndTurnAllowed() && QueueDisplay.size() > 0) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }

    @SpirePostfixPatch
    public static void postfix(EndTurnButton __instance) {
        if (suppressEndTurn) return;
        if (!CrossSpireMod.isConnected()) return;

        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.partyId = localPartyId();
        String endTurn = Protocol.GSON.toJson(msg);
        boolean localLeader = PartyCoordinator.isLeader(
            CrossSpireMod.partyManager, msg.partyId, CrossSpireMod.playerId);
        if (CrossSpireMod.isRoomHost() && !localLeader && CrossSpireMod.messageRouter != null) {
            // A host has no loopback socket; route its own readiness through the normal host path.
            CrossSpireMod.messageRouter.route(endTurn);
        } else if (!CrossSpireMod.isRoomHost()) {
            CrossSpireMod.send(endTurn);
        }
        // The RoomHost routes this back to a remote leader; a local leader can record directly.
        if (localLeader
            && CrossSpireMod.partyEndTurnTracker != null) {
            PartyState party = CrossSpireMod.partyManager.getParty(msg.partyId);
            if (CrossSpireMod.partyEndTurnTracker.markReady(party, CrossSpireMod.playerId)) {
                CrossSpireMod.partyEndTurnTracker.clear(msg.partyId);
                crossspire.combat.CombatPhaseCoordinator.broadcast(
                    msg.partyId, crossspire.combat.CombatPhase.PRE_MONSTER_TURN);
                crossspire.combat.CombatPhaseCoordinator.broadcast(
                    msg.partyId, crossspire.combat.CombatPhase.MONSTER_TURN);
            }
        }
        BaseMod.logger.info("EndTurnSync broadcast player_end_turn");
    }

    private static String localPartyId() {
        if (CrossSpireMod.partyManager == null) return PartyManager.DEFAULT_PARTY_ID;
        String partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        return partyId != null ? partyId : PartyManager.DEFAULT_PARTY_ID;
    }
}
