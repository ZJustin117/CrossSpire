package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class RoomPanel implements PostRenderSubscriber {

    private static final float X, Y, ROW_H;

    static {
        X = 20.0F * Settings.xScale;
        Y = Settings.HEIGHT / 2.0F + 100.0F * Settings.yScale;
        ROW_H = 22.0F * Settings.yScale;
    }

    public RoomPanel() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        float y = Y;
        boolean connected = CrossSpireMod.isConnected();

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardTitleFont,
            "CrossSpire Room", X, y, Color.YELLOW);
        y -= ROW_H + 4;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Status: " + (connected ? "Connected" : "Disconnected"), X, y,
            connected ? Color.GREEN : Color.RED);
        y -= ROW_H;

        String pid = CrossSpireMod.playerId;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Player: " + (pid.isEmpty() ? "(none)" : pid.substring(0, 8)), X, y, Color.WHITE);
        y -= ROW_H;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Room: " + ServerPicker.roomCode, X, y, Color.CYAN);
        y -= ROW_H + 4;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Commands: crossspire connect/disconnect/ready/status", X, y, Color.LIGHT_GRAY);
        y -= ROW_H;

        int count = RemotePlayerRegistry.count();
        if (count > 0) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Remote Players (" + count + "):", X, y, Color.CYAN);
            y -= ROW_H;
            for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
                String info = "  " + rp.playerId.substring(0, 8);
                if (rp.characterClass != null && !rp.characterClass.isEmpty()) {
                    info += " (" + rp.characterClass + ")";
                }
                info += " HP:" + rp.hp + "/" + rp.maxHp;
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, info, X, y, Color.WHITE);
                y -= ROW_H;
            }
        }
    }
}
