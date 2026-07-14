package crossspire.remote;

import basemod.DevConsole;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import crossspire.CrossSpireMod;
import crossspire.EventSuppression;
import java.util.ArrayList;

public class GameStarter {

    public static String start(String characterName, String seed) {
        return start(characterName, seed, true);
    }

    public static String startMinimal(String characterName, String seed) {
        return start(characterName, seed, false);
    }

    private static String start(String characterName, String seed, boolean createDungeon) {
        if (AbstractDungeon.player != null) {
            DevConsole.log("Game already started, ignoring crossspire start");
            return null;
        }

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

        EventSuppression.suppressEvents(() -> {
            AbstractDungeon.player = player;
            CrossSpireMod.localPlayer = player;
            AbstractDungeon.generateSeeds();
            if (createDungeon) {
                new Exordium(player, new ArrayList<String>());
            }
            CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;
        });

        String usedSeed = SeedHelper.getUserFacingSeedString();
        DevConsole.log("Game started as " + characterName + " seed=" + usedSeed);
        return usedSeed;
    }

    public static String start(String characterName) {
        return start(characterName, null, true);
    }
}
