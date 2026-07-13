package crossspire.combat;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

@SpirePatch(clz = AbstractMonster.class, method = "takeTurn")
public class MonsterTurnPatches {

    @SpirePrefixPatch
    public static void prefix(AbstractMonster __instance) {
        if (CrossSpireMod.stageHost != null && !CrossSpireMod.stageHost.isStageHost()) {
            BaseMod.logger.info("MonsterTurnPatches suppressing events for " + __instance.id);
            EventSuppression.SUPPRESSION.incrementAndGet();
        }
    }

    @SpirePostfixPatch
    public static void postfix(AbstractMonster __instance) {
        if (CrossSpireMod.stageHost != null) {
            if (!CrossSpireMod.stageHost.isStageHost()) {
                EventSuppression.SUPPRESSION.decrementAndGet();
                BaseMod.logger.info("MonsterTurnPatches unsuppressed for " + __instance.id);
                return;
            }

            Protocol.EffectDescription[] effects = collectEffects(__instance);
            if (effects.length > 0) {
                Protocol.CombatResultMessage result = new Protocol.CombatResultMessage();
                result.source = CrossSpireMod.playerId;
                result.seq = (int) (System.currentTimeMillis() % 100000);
                result.monsterId = __instance.id;
                result.effects = effects;
                result.operationSequence = new Protocol.OperationStep[0];

                if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
                    CrossSpireMod.relayClient.send(Protocol.GSON.toJson(result));
                    BaseMod.logger.info("MonsterTurnPatches broadcast combat_result: " + __instance.id + " effects=" + effects.length);
                }
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
}
