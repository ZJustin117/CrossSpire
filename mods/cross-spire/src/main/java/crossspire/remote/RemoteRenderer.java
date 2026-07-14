package crossspire.remote;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;
import crossspire.resource.RemoteCharacterResource;
import crossspire.resource.RemoteResourceManager;

public class RemoteRenderer implements PostRenderSubscriber, PostUpdateSubscriber {

    private static final float PANEL_X;
    private static final float PANEL_Y_TOP;
    private static final float LINE_HEIGHT;

    static {
        PANEL_X = 20.0F * Settings.xScale;
        PANEL_Y_TOP = Settings.HEIGHT - 60.0F * Settings.yScale;
        LINE_HEIGHT = 24.0F * Settings.yScale;
    }

    public RemoteRenderer() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostUpdate() {
        if (!CrossSpireMod.isConnected()) return;
        if (AbstractDungeon.player == null) return;

        int idx = 0;
        int total = RemotePlayerRegistry.count();
        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            RemoteCharacterResource chr = rp.getCharacterResource();
            if (chr == null) {
                RemoteCharacterResource loaded = RemoteResourceManager.getCharacter(rp.playerId);
                rp.setCharacterResource(loaded);
                chr = loaded;
            }
            if (chr != null && chr.isLoaded()) {
                chr.drawX = remotePosX(idx, total);
                chr.drawY = Settings.HEIGHT * 0.25f;
                chr.scaleX = 1.0f;
                chr.scaleY = 1.0f;
                chr.update(Gdx.graphics.getDeltaTime());
            }
            rp.getPlayerInstance();
            rp.syncToPlayerInstance();
            idx++;
        }
    }

    private float remotePosX(int index, int total) {
        float cx = Settings.WIDTH * 0.5f;
        float spread = 250f * Settings.xScale;
        if (total == 1) return cx - spread;
        float step = spread * 2f / (total - 1);
        return cx - spread + step * index;
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (com.megacrit.cardcrawl.helpers.FontHelper.tipBodyFont == null) return;
        try {
            if (!CrossSpireMod.isConnected()) return;
            if (AbstractDungeon.player == null) return;
            if (AbstractDungeon.getCurrRoom() == null) return;
            if (AbstractDungeon.getCurrRoom().monsters == null) return;
            render(sb);
        } catch (Exception e) {
        }
    }

    private void render(SpriteBatch sb) {
        int count = RemotePlayerRegistry.count();
        if (count == 0) return;

        float y = PANEL_Y_TOP;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Remote (" + count + ")", PANEL_X, y, Color.YELLOW);
        y -= LINE_HEIGHT;

        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            RemoteCharacterResource chr = rp.getCharacterResource();
            RemotePlayer remotePlayer = rp.getPlayerInstance();

            if (chr != null && chr.isLoaded()) {
                chr.render(sb);
            }

            if (remotePlayer != null) {
                remotePlayer.renderHealth(sb);
            }

            String hpText = rp.hp + "/" + rp.maxHp + "HP  " + rp.block + "B  E:" + rp.energy;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                rp.playerId.substring(0, 8) + " " + hpText,
                PANEL_X, y, Color.WHITE);
            y -= LINE_HEIGHT;
        }
    }
}
