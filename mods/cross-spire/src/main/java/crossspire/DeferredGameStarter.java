package crossspire;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;

public class DeferredGameStarter {

    private static int skipFrames = 0;
    private static String deferredSeed = null;
    private static String deferredChar = null;

    @SpirePatch(clz = com.megacrit.cardcrawl.core.CardCrawlGame.class, method = "render")
    public static class RenderPrefix {
        @SpirePrefixPatch
        public static void Prefix() {
            if (CrossSpireMod.deferredSeed != null && skipFrames == 0) {
                deferredSeed = CrossSpireMod.deferredSeed;
                deferredChar = CrossSpireMod.deferredChar;
                CrossSpireMod.deferredSeed = null;
                CrossSpireMod.deferredChar = null;
                skipFrames = 3; // skip 3 frames for old render to finish
                return;
            }

            if (skipFrames > 0) {
                skipFrames--;
                if (skipFrames == 0 && deferredSeed != null) {
                    BaseMod.logger.info("DeferredGameStarter executing: " + deferredChar + " seed=" + deferredSeed);
                    String usedSeed = crossspire.remote.GameStarter.start(deferredChar, deferredSeed);
                    if (usedSeed != null) {
                        CrossSpireMod.lastStartedChar = deferredChar;
                        CrossSpireMod.lastStartedSeed = usedSeed;
                        CrossSpireMod.startedGame = true;
                    }
                    deferredSeed = null;
                    deferredChar = null;
                }
            }
        }
    }
}
