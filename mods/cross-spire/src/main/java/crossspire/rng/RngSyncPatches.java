package crossspire.rng;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.CrossSpireMod;

@SpirePatch(clz = AbstractDungeon.class, method = "generateSeeds", paramtypez = {})
public class RngSyncPatches {

    @SpirePostfixPatch
    public static void postfix() {
        if (CrossSpireMod.syncedSeed != null && !CrossSpireMod.syncedSeed.isEmpty()) {
            try {
                long seed = Long.parseLong(CrossSpireMod.syncedSeed);
                CrossSpireMod.rngManager = new RngManager(seed);
                BaseMod.logger.info("RngSyncPatches synced RNG seed=" + seed);
            } catch (NumberFormatException e) {
                BaseMod.logger.info("RngSyncPatches invalid seed: " + CrossSpireMod.syncedSeed);
            }
        }
    }
}
