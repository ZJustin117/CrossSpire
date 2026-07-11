package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import crossspire.CrossSpireMod;

public class LobbyScreen implements PostInitializeSubscriber, PostRenderSubscriber {

    private String statusText = "Disconnected";
    private boolean visible = false;

    public LobbyScreen() {
        BaseMod.subscribe(this);
    }

    private boolean loggedOnce = false;

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

        if (!loggedOnce) {
            String connected = CrossSpireMod.isConnected() ? "[Connected]" : "[Not Connected]";
            BaseMod.logger.info("LobbyScreen: " + connected + " " + ServerPicker.serverUrl + " Room:" + ServerPicker.roomCode);
            loggedOnce = true;
        }
    }
}
