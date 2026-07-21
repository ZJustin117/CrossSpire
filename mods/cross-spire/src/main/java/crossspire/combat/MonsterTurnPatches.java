package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage-host monster turn authority (P6 / ARCHITECTURE §10).
 * HP delta on local player around {@link MonsterGroup#applyPreTurnLogic()}.
 * Non-stage-hosts do not broadcast; they rely on AUTHORITATIVE_APPLY of combat_result.
 */
@SuppressWarnings("unused")
public class MonsterTurnPatches {

    private static MonsterTurnCapture.Snapshot preTurnPlayer;

    @SpirePatch(clz = AbstractMonster.class, method = "usePreBattleAction", paramtypez = {})
    public static class PreBattle {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            List<Protocol.EffectDescription> list = new ArrayList<Protocol.EffectDescription>();
            if (__instance.currentBlock > 0) {
                Protocol.EffectDescription blk = new Protocol.EffectDescription();
                blk.kind = "gain_block";
                blk.target = __instance.id;
                blk.amount = __instance.currentBlock;
                list.add(blk);
            }

            Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
            result.source = CrossSpireMod.playerId;
            result.seq = CrossSpireMod.nextSeq();
            result.monsterId = __instance.id;
            result.effects = list.toArray(new Protocol.EffectDescription[0]);
            result.operationSequence = new Protocol.OperationStep[0];

            CrossSpireMod.send(Protocol.GSON.toJson(result));
        }
    }

    @SpirePatch(clz = MonsterGroup.class, method = "applyPreTurnLogic", paramtypez = {})
    public static class PreTurnLogic {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(MonsterGroup __instance) {
            preTurnPlayer = null;
            boolean isStageHost = CrossSpireMod.stageHost != null
                && CrossSpireMod.stageHost.isStageHost();
            if (!CombatTurnOrchestrator.shouldAllowMonsterAi(
                    CrossSpireMod.isConnected(), isStageHost,
                    CombatPhaseCoordinator.getCurrentPhase())) {
                return SpireReturn.Return(null);
            }
            if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();
            if (AbstractDungeon.player == null) return SpireReturn.Continue();

            preTurnPlayer = new MonsterTurnCapture.Snapshot(
                AbstractDungeon.player.currentHealth,
                AbstractDungeon.player.currentBlock,
                "self");
            BaseMod.logger.info("MonsterTurnPatches pre-turn snapshot hp="
                + preTurnPlayer.hp + " block=" + preTurnPlayer.block);
            return SpireReturn.Continue();
        }

        @SpirePostfixPatch
        public static void postfix(MonsterGroup __instance) {
            if (preTurnPlayer == null) return;
            if (!CombatTurnOrchestrator.shouldStageHostRunMonsterAi()) {
                preTurnPlayer = null;
                return;
            }
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) {
                preTurnPlayer = null;
                return;
            }
            if (AbstractDungeon.player == null) {
                preTurnPlayer = null;
                return;
            }

            MonsterTurnCapture.Snapshot after = new MonsterTurnCapture.Snapshot(
                AbstractDungeon.player.currentHealth,
                AbstractDungeon.player.currentBlock,
                "self");
            MonsterTurnCapture.Delta delta = MonsterTurnCapture.diff(preTurnPlayer, after);
            preTurnPlayer = null;

            Protocol.EffectDescription[] effects = MonsterTurnCapture.playerHpLossEffects(delta.damageToPlayer);
            Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
            result.source = CrossSpireMod.playerId;
            result.seq = CrossSpireMod.nextSeq();
            result.monsterId = "monster_turn";
            result.turnTransactionId = CombatPhaseCoordinator.getLastTransactionId();
            result.effects = effects;
            result.operationSequence = new Protocol.OperationStep[0];

            CrossSpireMod.send(Protocol.GSON.toJson(result));
            BaseMod.logger.info("MonsterTurnPatches combat_result damage=" + delta.damageToPlayer);

            // Room host advances phase after stage-host capture (solo host+stage common).
            if (CrossSpireMod.isRoomHost()) {
                CombatPhaseCoordinator.broadcast(CombatPhase.POST_MONSTER_TURN);
                CombatPhaseCoordinator.broadcast(CombatPhase.PLAYER_TURN);
            }
        }
    }
}
