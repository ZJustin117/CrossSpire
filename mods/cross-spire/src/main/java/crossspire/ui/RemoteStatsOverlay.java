package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class RemoteStatsOverlay implements PostRenderSubscriber {

    private Texture whitePixel;

    private static final Color HP_BAR_BG = new Color(0.0F, 0.0F, 0.0F, 0.6F);
    private static final Color HP_GREEN = new Color(0.0F, 0.8F, 0.2F, 0.9F);
    private static final Color HP_YELLOW = new Color(0.9F, 0.8F, 0.1F, 0.9F);
    private static final Color HP_RED = new Color(0.8F, 0.1F, 0.1F, 0.9F);
    private static final Color BLOCK_COLOR = new Color(0.3F, 0.6F, 0.9F, 1.0F);

    public RemoteStatsOverlay() {
        BaseMod.subscribe(this);
    }

    private void ensureTexture() {
        if (whitePixel == null) {
            try {
                Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                px.setColor(Color.WHITE);
                px.fill();
                whitePixel = new Texture(px);
                px.dispose();
            } catch (Exception e) {
                BaseMod.logger.error("RemoteStatsOverlay texture init failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (AbstractDungeon.getCurrRoom() == null) return;
        if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) return;
        int count = RemotePlayerRegistry.count();
        if (count == 0) return;

        ensureTexture();
        if (whitePixel == null) return;

        float panelX = Settings.WIDTH - 160.0F * Settings.xScale;
        float panelY = Settings.HEIGHT - 40.0F * Settings.yScale;
        float hpBarW = 120.0F * Settings.xScale;
        float hpBarH = 10.0F * Settings.yScale;
        float lineH = 20.0F * Settings.yScale;
        float lineHMini = 16.0F * Settings.yScale;

        float y = panelY;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardTitleFont, "Remote", panelX, y, Color.YELLOW);
        y -= lineH;

        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            y = drawPlayerRow(sb, rp, panelX, y, hpBarW, hpBarH, lineH, lineHMini, lineH);
        }
    }

    private float drawPlayerRow(SpriteBatch sb, RemotePlayerState rp, float x, float y,
                                 float barW, float barH, float lineH, float lineHMini, float spacing) {
        String label = rp.playerId.substring(0, 8);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, label, x, y, Color.WHITE);
        y -= lineH;

        float ratio = rp.maxHp > 0 ? (float) rp.hp / rp.maxHp : 0;
        ratio = Math.max(0, Math.min(1, ratio));

        sb.setColor(HP_BAR_BG);
        sb.draw(whitePixel, x, y - barH, barW, barH);
        sb.setColor(Color.WHITE);

        Color barColor = ratio > 0.5F ? HP_GREEN : ratio > 0.25F ? HP_YELLOW : HP_RED;
        sb.setColor(barColor);
        sb.draw(whitePixel, x + 1, y - barH + 1, (barW - 2) * ratio, barH - 2);
        sb.setColor(Color.WHITE);

        String hpText = rp.hp + "/" + rp.maxHp;
        if (rp.block > 0) {
            hpText += " B:" + rp.block;
            sb.setColor(BLOCK_COLOR);
        } else {
            sb.setColor(barColor);
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, hpText, x + barW + 4, y, sb.getColor());

        return y - barH - 4;
    }
}
