package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

public class MonsterTurnPatches {

    private static int preTurnPlayerHp = 0;
    private static int preTurnPlayerBlock = 0;

    @SpirePatch(clz = AbstractMonster.class, method = "usePreBattleAction", paramtypez = {})
    public static class PreBattle {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            broadcastMonsterEffects(__instance);
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "applyStartOfTurnPowers", paramtypez = {})
    public static class BeforeTurn {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (AbstractDungeon.player == null) return;
            preTurnPlayerHp = AbstractDungeon.player.currentHealth;
            preTurnPlayerBlock = AbstractDungeon.player.currentBlock;
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "applyStartOfTurnPowers", paramtypez = {})
    public static class AfterMonsterTurns {
        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance) {
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
            if (AbstractDungeon.player == null) return;

            int dmg = preTurnPlayerHp - AbstractDungeon.player.currentHealth;
            int blockDiff = AbstractDungeon.player.currentBlock - preTurnPlayerBlock;

            List<Protocol.EffectDescription> list = new ArrayList<>();
            if (dmg > 0) {
                Protocol.EffectDescription eff = new Protocol.EffectDescription();
                eff.kind = "damage";
                eff.target = "self";
                eff.amount = dmg;
                eff.damageType = "NORMAL";
                list.add(eff);
            }
            if (blockDiff > 0) {
                Protocol.EffectDescription eff = new Protocol.EffectDescription();
                eff.kind = "gain_block";
                eff.target = "self";
                eff.amount = blockDiff;
                list.add(eff);
            }

            if (!list.isEmpty()) {
                Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
                result.source = CrossSpireMod.playerId;
                result.seq = CrossSpireMod.nextSeq();
                result.monsterId = "monsters";
                result.effects = list.toArray(new Protocol.EffectDescription[0]);
                result.operationSequence = new Protocol.OperationStep[0];

                CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
                BaseMod.logger.info("MonsterTurnPatches turnResult: dmg=" + dmg + " block=" + blockDiff);
            }
        }
    }

    private static Protocol.EffectDescription[] collectEffects(AbstractMonster monster) {
        List<Protocol.EffectDescription> list = new ArrayList<>();
        if (monster.getIntentBaseDmg() > 0 && AbstractDungeon.player != null) {
            Protocol.EffectDescription dmg = new Protocol.EffectDescription();
            dmg.kind = "damage";
            dmg.target = "self";
            dmg.amount = monster.getIntentBaseDmg();
            dmg.damageType = "NORMAL";
            list.add(dmg);
        }
        if (monster.currentBlock > 0 && AbstractDungeon.player != null) {
            Protocol.EffectDescription blk = new Protocol.EffectDescription();
            blk.kind = "gain_block";
            blk.target = monster.id;
            blk.amount = monster.currentBlock;
            list.add(blk);
        }
        return list.toArray(new Protocol.EffectDescription[0]);
    }

    private static void broadcastMonsterEffects(AbstractMonster monster) {
        if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;

        Protocol.EffectDescription[] effects = collectEffects(monster);
        Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
        result.source = CrossSpireMod.playerId;
        result.seq = CrossSpireMod.nextSeq();
        result.monsterId = monster.id;
        result.effects = effects;
        result.operationSequence = new Protocol.OperationStep[0];

        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
        BaseMod.logger.info("MonsterTurnPatches preBattle: " + monster.id
            + " effects=" + effects.length);
    }
}
