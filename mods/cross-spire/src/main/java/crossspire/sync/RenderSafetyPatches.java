package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

@SuppressWarnings("unused")
public class RenderSafetyPatches {

    public static volatile boolean remoteCombatActive = false;

    @SpirePatch(clz = com.megacrit.cardcrawl.vfx.combat.BattleStartEffect.class,
            method = "update", paramtypez = {})
    public static class BattleStartSafety {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(com.megacrit.cardcrawl.vfx.combat.BattleStartEffect __instance) {
            if (remoteCombatActive) {
                __instance.isDone = true;
                BaseMod.logger.info("RenderSafety: suppressed BattleStartEffect in remote combat");
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
