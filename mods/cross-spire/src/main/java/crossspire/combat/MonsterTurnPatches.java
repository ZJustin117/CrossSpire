package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
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
            if (CrossSpireMod.stageHost == null || !CrossSpireMod.stageHost.isStageHost()) return;
            if (!CrossSpireMod.isConnected()) return;

            List<Protocol.EffectDescription> list = new ArrayList<>();
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
}
