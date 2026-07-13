package crossspire.sync;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import crossspire.CrossSpireMod;
import java.util.ArrayList;

public class AutoGameStartPatch {

    public static String pendingChar = null;

    @SpirePatch(clz = com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen.class, method = "open")
    public static class AutoStart {
        @SpirePostfixPatch
        public static void Postfix() {
            if (pendingChar == null) return;
            String charName = pendingChar;
            pendingChar = null;

            BaseMod.logger.info("AutoGameStartPatch: " + charName + " seed=" + SeedHelper.cachedSeed);

            try {
                AbstractPlayer.PlayerClass pc = AbstractPlayer.PlayerClass.valueOf(charName.toUpperCase());
                CharacterManager manager = new CharacterManager();
                AbstractPlayer player = manager.setChosenCharacter(pc);
                if (player == null) return;

                AbstractDungeon.player = player;
                CrossSpireMod.localPlayer = player;
                AbstractDungeon.generateSeeds();
                new Exordium(player, new ArrayList<String>());
                CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;

                BaseMod.logger.info("AutoGameStartPatch: game started as " + charName);
            } catch (Exception e) {
                BaseMod.logger.error("AutoGameStartPatch failed: " + e.getMessage());
            }
        }
    }
}
