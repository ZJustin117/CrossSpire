package crossspire.remote;

import basemod.DevConsole;
import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import crossspire.CrossSpireMod;
import java.lang.reflect.Field;

/**
 * Boots a run from main menu without the char-select UI.
 * Must run on the GL/update thread ({@code Gdx.app.postRunnable}).
 *
 * Mirrors official char-select confirm: set {@code chosenCharacter} + seed,
 * then {@code MainMenuScreen.isFadingOut/fadedOut}. {@code CardCrawlGame.update}
 * creates the player, {@code DungeonTransitionScreen}, and switches to GAMEPLAY.
 *
 * Directly assigning {@code mode=GAMEPLAY} + {@code new Exordium(...)} races the
 * menu render path and can NPE in {@code renderBlackFadeScreen} / GlyphLayout.
 */
public class GameStarter {

    public static String start(String characterName, String seed) {
        if (AbstractDungeon.player != null
                && CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY) {
            DevConsole.log("Game already started, ignoring crossspire start");
            return null;
        }
        if (CardCrawlGame.mainMenuScreen != null
                && CardCrawlGame.mainMenuScreen.isFadingOut) {
            DevConsole.log("Game start already in progress");
            return null;
        }

        final PlayerClass playerClass;
        try {
            playerClass = PlayerClass.valueOf(characterName.toUpperCase());
        } catch (IllegalArgumentException e) {
            DevConsole.log("Unknown character: " + characterName);
            return null;
        }

        if (seed != null && seed.length() > 0) {
            SeedHelper.setSeed(seed);
        } else {
            long ts = System.currentTimeMillis();
            SeedHelper.setSeed(String.valueOf(ts % 900000L + 100000L));
        }

        ensureScreenColor();

        // Same handoff as CharacterSelectScreen confirm.
        CardCrawlGame.chosenCharacter = playerClass;
        AbstractDungeon.generateSeeds();

        if (CardCrawlGame.mainMenuScreen == null) {
            DevConsole.log("Main menu not ready; cannot start");
            return null;
        }

        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        // Skip multi-frame overlay fade so the next update() builds transition/dungeon.
        CardCrawlGame.mainMenuScreen.fadedOut = true;

        String usedSeed = SeedHelper.getUserFacingSeedString();
        DevConsole.log("Game start queued as " + characterName + " seed=" + usedSeed);
        return usedSeed;
    }

    public static String start(String characterName) {
        return start(characterName, null);
    }

    /** After transition, bind CrossSpire local player to the engine instance. */
    public static void bindLocalPlayerIfReady() {
        if (AbstractDungeon.player != null) {
            CrossSpireMod.localPlayer = AbstractDungeon.player;
        }
    }

    private static void ensureScreenColor() {
        try {
            Field f = CardCrawlGame.class.getDeclaredField("screenColor");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, Color.BLACK.cpy());
            }
        } catch (Exception ignored) {
        }
    }
}
