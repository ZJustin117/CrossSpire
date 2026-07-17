package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;

public class LobbyScreen implements PostRenderSubscriber {

    private String statusText = "Disconnected";
    private boolean visible = false;
    private boolean waitingForHost = false;

    public LobbyScreen() {
        BaseMod.subscribe(this);
    }

    public void setStatus(String text) {
        statusText = text;
        BaseMod.logger.info("LobbyScreen status: " + text);
    }

    public void setWaitingForHost(boolean waiting) {
        this.waitingForHost = waiting;
        if (waiting) BaseMod.logger.info("LobbyScreen: waiting for host...");
    }

    public void show() { visible = true; }
    public void hide() { visible = false; }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (!visible) return;
        if (FontHelper.tipBodyFont == null) return;

        String line = "CrossSpire: " + statusText;
        if (CrossSpireMod.isConnected()) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                line, 10, 60, Color.GREEN);
        } else {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                line, 10, 60, Color.GRAY);
        }

        if (waitingForHost) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Waiting for host...", 10, 44, Color.RED);
        }
    }
}
