package crossspire.sync;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;

public class GameStartCompatPatch {

    @SpirePatch(clz = com.megacrit.cardcrawl.core.CardCrawlGame.class, method = "renderBlackFadeScreen", paramtypez = {})
    public static class RenderBlackFadePatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(com.megacrit.cardcrawl.core.CardCrawlGame __instance) {
            try {
                if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.player == null) {
                    return SpireReturn.Return(null);
                }
                if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.getCurrRoom() == null) {
                    return SpireReturn.Return(null);
                }
            } catch (Exception e) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
