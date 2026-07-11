package crossspire.remote;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.localization.CharacterStrings;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import com.megacrit.cardcrawl.screens.stats.CharStat;
import java.util.ArrayList;

public class RemotePlayer extends AbstractPlayer {

    private final String remotePlayerId;

    public RemotePlayer(String name, PlayerClass playerClass, String remotePlayerId) {
        super(name, playerClass);
        this.remotePlayerId = remotePlayerId;
    }

    public String getRemotePlayerId() {
        return remotePlayerId;
    }

    @Override
    public String getPortraitImageName() { return null; }

    @Override
    public ArrayList<String> getStartingDeck() { return new ArrayList<String>(); }

    @Override
    public ArrayList<String> getStartingRelics() { return new ArrayList<String>(); }

    @Override
    public CharSelectInfo getLoadout() { return null; }

    @Override
    public String getTitle(PlayerClass pc) { return "Remote"; }

    @Override
    public AbstractCard.CardColor getCardColor() { return AbstractCard.CardColor.COLORLESS; }

    @Override
    public Color getCardRenderColor() { return Color.WHITE; }

    @Override
    public String getAchievementKey() { return ""; }

    @Override
    public ArrayList<AbstractCard> getCardPool(ArrayList<AbstractCard> pool) { return pool; }

    @Override
    public AbstractCard getStartCardForEvent() { return null; }

    @Override
    public Color getCardTrailColor() { return Color.WHITE; }

    @Override
    public String getLeaderboardCharacterName() { return "Remote"; }

    @Override
    public Texture getEnergyImage() { return null; }

    @Override
    public int getAscensionMaxHPLoss() { return 0; }

    @Override
    public BitmapFont getEnergyNumFont() { return null; }

    @Override
    public void renderOrb(SpriteBatch sb, boolean flip, float x, float y) {}

    @Override
    public void updateOrb(int orbCount) {}

    @Override
    public Prefs getPrefs() { return null; }

    @Override
    public void loadPrefs() {}

    @Override
    public CharStat getCharStat() { return null; }

    @Override
    public int getUnlockedCardCount() { return 0; }

    @Override
    public int getSeenCardCount() { return 0; }

    @Override
    public int getCardCount() { return 0; }

    @Override
    public boolean saveFileExists() { return false; }

    @Override
    public String getWinStreakKey() { return ""; }

    @Override
    public String getLeaderboardWinStreakKey() { return ""; }

    @Override
    public void renderStatScreen(SpriteBatch sb, float x, float y) {}

    @Override
    public void doCharSelectScreenSelectEffect() {}

    @Override
    public String getCustomModeCharacterButtonSoundKey() { return ""; }

    @Override
    public Texture getCustomModeCharacterButtonImage() { return null; }

    @Override
    public CharacterStrings getCharacterString() { return null; }

    @Override
    public String getLocalizedCharacterName() { return "Remote"; }

    @Override
    public void refreshCharStat() {}

    @Override
    public TextureAtlas.AtlasRegion getOrb() { return null; }

    @Override
    public String getSpireHeartText() { return ""; }

    @Override
    public Color getSlashAttackColor() { return Color.WHITE; }

    @Override
    public AbstractGameAction.AttackEffect[] getSpireHeartSlashEffect() { return new AbstractGameAction.AttackEffect[0]; }

    @Override
    public String getVampireText() { return ""; }

    @Override
    public AbstractPlayer newInstance() {
        return new RemotePlayer(this.name, this.chosenClass, this.remotePlayerId);
    }
}
