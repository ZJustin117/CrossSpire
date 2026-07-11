package crossspire.remote;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;

public class RemoteRenderer implements PostRenderSubscriber {

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
    public void receivePostRender(SpriteBatch sb) {
        try {
            if (!CrossSpireMod.isConnected()) return;
            if (AbstractDungeon.player == null) return;
            if (AbstractDungeon.getCurrRoom() == null) return;
            if (AbstractDungeon.getCurrRoom().monsters == null) return;
            render(sb);
        } catch (Exception e) {
            // silently ignore during main menu / loading
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
            String hpText = rp.hp + "/" + rp.maxHp + "HP  " + rp.block + "B";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                rp.playerId.substring(0, 8) + " " + hpText,
                PANEL_X, y, Color.WHITE);
            y -= LINE_HEIGHT;
        }
    }
}
