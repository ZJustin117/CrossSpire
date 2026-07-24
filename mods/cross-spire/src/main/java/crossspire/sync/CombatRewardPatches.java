package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import crossspire.party.ActiveNodeTracker;
import crossspire.party.PartyManager;

/**
 * T7.8: reward phase enter on battle end; reward_done via console or screen complete when available.
 * Gold remains personal economy (projection only via GoldSyncPatches / player_state).
 */
public class CombatRewardPatches {

    public static boolean suppressRewardSync = false;
    private static boolean localDoneSent = false;

    @SpirePatch(clz = AbstractRoom.class, method = "endBattle", paramtypez = {}, optional = true)
    public static class OnEndBattle {
        @SpirePostfixPatch
        public static void Postfix(AbstractRoom __instance) {
            if (suppressRewardSync || EventSuppression.isSuppressed()) return;
            if (!CrossSpireMod.isConnected()) return;
            String nodeInstanceId = ActiveNodeTracker.getNodeInstanceId();
            if (nodeInstanceId == null || nodeInstanceId.isEmpty()) {
                BaseMod.logger.info("CombatRewardPatches endBattle skip: no active node_instance");
                return;
            }
            if (CrossSpireMod.rewardPhaseTracker != null
                && CrossSpireMod.rewardPhaseTracker.isCompletedNode(nodeInstanceId)) {
                BaseMod.logger.info("CombatRewardPatches endBattle skip completed node="
                    + nodeInstanceId);
                return;
            }
            if (CrossSpireMod.rewardPhaseTracker != null
                && CrossSpireMod.rewardPhaseTracker.isActive()
                && nodeInstanceId.equals(CrossSpireMod.rewardPhaseTracker.getNodeInstanceId())) {
                BaseMod.logger.info("CombatRewardPatches endBattle skip already active node="
                    + nodeInstanceId);
                return;
            }
            localDoneSent = false;
            String partyId = ActiveNodeTracker.getPartyId();
            if (partyId == null || partyId.isEmpty()) partyId = PartyManager.DEFAULT_PARTY_ID;

            Protocol.RewardPhaseEnter enter = new Protocol.RewardPhaseEnter();
            enter.source = CrossSpireMod.playerId;
            enter.seq = CrossSpireMod.nextSeq();
            enter.partyId = partyId;
            enter.nodeInstanceId = nodeInstanceId;
            enter.transactionId = "reward-" + nodeInstanceId;
            String json = Protocol.GSON.toJson(enter);
            CrossSpireMod.send(json);
            if (CrossSpireMod.messageRouter != null) {
                CrossSpireMod.messageRouter.route(json);
            }
            BaseMod.logger.info("CombatRewardPatches reward_phase_enter node=" + nodeInstanceId
                + " (full CombatRewardScreen is local STS; personal gold)");
        }
    }

    /** Explicit proceed / harness: mark local reward screen finished. */
    public static void sendRewardDone(String reason) {
        if (!CrossSpireMod.isConnected()) return;
        if (localDoneSent) {
            BaseMod.logger.info("CombatRewardPatches reward_done already sent");
            return;
        }
        String nodeInstanceId = ActiveNodeTracker.getNodeInstanceId();
        if (nodeInstanceId == null || nodeInstanceId.isEmpty()) {
            if (CrossSpireMod.rewardPhaseTracker != null
                && CrossSpireMod.rewardPhaseTracker.isActive()) {
                nodeInstanceId = CrossSpireMod.rewardPhaseTracker.getNodeInstanceId();
            }
        }
        if (nodeInstanceId == null || nodeInstanceId.isEmpty()) return;

        String partyId = ActiveNodeTracker.getPartyId();
        if (partyId == null || partyId.isEmpty()) {
            partyId = CrossSpireMod.rewardPhaseTracker != null
                ? CrossSpireMod.rewardPhaseTracker.getPartyId() : PartyManager.DEFAULT_PARTY_ID;
        }
        if (partyId == null || partyId.isEmpty()) partyId = PartyManager.DEFAULT_PARTY_ID;

        Protocol.RewardDone done = new Protocol.RewardDone();
        done.source = CrossSpireMod.playerId;
        done.seq = CrossSpireMod.nextSeq();
        done.partyId = partyId;
        done.nodeInstanceId = nodeInstanceId;
        String json = Protocol.GSON.toJson(done);
        localDoneSent = true;
        CrossSpireMod.send(json);
        if (CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(json);
        }
        BaseMod.logger.info("CombatRewardPatches reward_done reason=" + reason
            + " node=" + nodeInstanceId);
    }
}
