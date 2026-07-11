package crossspire.remote;

import basemod.DevConsole;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import java.util.ArrayList;

public class GameStarter {

    public static String start(String characterName, String seed) {
        PlayerClass playerClass;
        try {
            playerClass = PlayerClass.valueOf(characterName.toUpperCase());
        } catch (IllegalArgumentException e) {
            DevConsole.log("Unknown character: " + characterName);
            return null;
        }

        CharacterManager manager = new CharacterManager();
        AbstractPlayer player = manager.setChosenCharacter(playerClass);
        if (player == null) {
            DevConsole.log("Failed to create character: " + characterName);
            return null;
        }

        if (seed != null && seed.length() > 0) {
            SeedHelper.setSeed(seed);
        } else if (SeedHelper.cachedSeed == null) {
            SeedHelper.setSeed("");
        }

        AbstractDungeon.generateSeeds();
        new Exordium(player, new ArrayList<String>());
        CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;

        String usedSeed = SeedHelper.getUserFacingSeedString();
        DevConsole.log("Game started as " + characterName + " seed=" + usedSeed);
        return usedSeed;
    }

    public static String start(String characterName) {
        return start(characterName, null);
    }
}
