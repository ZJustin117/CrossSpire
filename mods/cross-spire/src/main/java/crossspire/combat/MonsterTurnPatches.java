package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class MonsterTurnPatches {

    private static int preTurnHp = 0;
    private static int preTurnBlock = 0;

    @SpirePatch(clz = AbstractDungeon.class, method = "applyStartOfTurnPowers", paramtypez = {})
    public static class BeforeMonsterTurns {
        @SpirePostfixPatch
        public static void postfix() {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;
            if (AbstractDungeon.player == null) return;

            if (preTurnHp != 0) {
                int hpDelta = preTurnHp - AbstractDungeon.player.currentHealth;
                int blockDelta = AbstractDungeon.player.currentBlock - preTurnBlock;

                if (hpDelta != 0 || blockDelta != 0) {
                    broadcastMonsterTurnResult(hpDelta, blockDelta);
                }
            }

            preTurnHp = AbstractDungeon.player.currentHealth;
            preTurnBlock = AbstractDungeon.player.currentBlock;
        }

        private static void broadcastMonsterTurnResult(int hpDelta, int blockDelta) {
            List<Protocol.EffectDescription> list = new ArrayList<>();
            if (hpDelta > 0) {
                Protocol.EffectDescription dmg = new Protocol.EffectDescription();
                dmg.kind = "damage";
                dmg.target = "self";
                dmg.amount = hpDelta;
                dmg.damageType = "NORMAL";
                list.add(dmg);
            }
            if (blockDelta > 0) {
                Protocol.EffectDescription blk = new Protocol.EffectDescription();
                blk.kind = "gain_block";
                blk.target = "self";
                blk.amount = blockDelta;
                list.add(blk);
            }

            Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
            result.source = CrossSpireMod.playerId;
            result.seq = CrossSpireMod.nextSeq();
            result.effects = list.toArray(new Protocol.EffectDescription[0]);
            result.operationSequence = new Protocol.OperationStep[0];

            CrossSpireMod.send(Protocol.GSON.toJson(result));
            BaseMod.logger.info("MonsterTurnPatches turn result: hpDelta=" + hpDelta
                + " blockDelta=" + blockDelta);
        }
    }
}
