package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;

public class LobbyScreen implements PostInitializeSubscriber, PostRenderSubscriber {

    private String statusText = "Disconnected";
    private boolean visible = false;

    public LobbyScreen() {
        BaseMod.subscribe(this);
    }

    public void setStatus(String text) {
        statusText = text;
        BaseMod.logger.info("LobbyScreen status: " + text);
    }

    public void show() { visible = true; }
    public void hide() { visible = false; }

    @Override
    public void receivePostInitialize() {
        BaseMod.logger.info("LobbyScreen initialized");
        CrossSpireMod.runStartupScript();
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (!visible) return;

        String line = "CrossSpire: " + statusText;
        if (CrossSpireMod.isConnected()) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                line, 10, 60, Color.GREEN);
        } else {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                line, 10, 60, Color.GRAY);
        }
    }
}
