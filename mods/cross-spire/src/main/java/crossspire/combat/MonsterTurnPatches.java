package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class MonsterTurnPatches {

    @SpirePatch(clz = AbstractMonster.class, method = "usePreBattleAction", paramtypez = {})
    public static class PreBattle {
        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance) {
            broadcastMonsterEffects(__instance);
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
        if (!CrossSpireMod.isConnected()) return;

        Protocol.EffectDescription[] effects = collectEffects(monster);
        Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
        result.source = CrossSpireMod.playerId;
        result.seq = CrossSpireMod.nextSeq();
        result.monsterId = monster.id;
        result.effects = effects;
        result.operationSequence = new Protocol.OperationStep[0];

        CrossSpireMod.send(Protocol.GSON.toJson(result));
        BaseMod.logger.info("MonsterTurnPatches preBattle: " + monster.id
            + " effects=" + effects.length);
    }
}
