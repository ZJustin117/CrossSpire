package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import crossspire.CrossSpireMod;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class RoomPanel implements PostRenderSubscriber, PostUpdateSubscriber {

    private Texture whitePixel;

    private static final float X, Y, ROW_H, BTN_W, BTN_H;
    private static final Color BG = new Color(0.0F, 0.0F, 0.1F, 0.75F);
    private static final Color BTN_BG = new Color(0.2F, 0.3F, 0.4F, 0.85F);
    private static final Color BTN_HOVER = new Color(0.3F, 0.5F, 0.6F, 0.9F);
    private static final Color BTN_PRESS = new Color(0.5F, 0.7F, 0.8F, 1.0F);
    private static final Color BTN_GREEN = new Color(0.15F, 0.5F, 0.2F, 0.85F);
    private static final Color BTN_ORANGE = new Color(0.6F, 0.4F, 0.1F, 0.85F);
    private static final Color BTN_RED = new Color(0.6F, 0.15F, 0.15F, 0.85F);
    private static final Color TITLE_COLOR = new Color(0.95F, 0.8F, 0.1F, 1.0F);
    private static final Color WHITE = new Color(1, 1, 1, 1);
    private static final Color CYAN = new Color(0.3F, 0.8F, 0.9F, 1.0F);
    private static final Color GREEN = new Color(0.2F, 0.8F, 0.2F, 1.0F);
    private static final Color RED = new Color(0.8F, 0.2F, 0.2F, 1.0F);

    private String selectedCharacter = "IRONCLAD";

    static {
        X = 20.0F * Settings.xScale;
        Y = Settings.HEIGHT - 60.0F * Settings.yScale;
        ROW_H = 24.0F * Settings.yScale;
        BTN_W = 140.0F * Settings.xScale;
        BTN_H = 28.0F * Settings.yScale;
    }

    public RoomPanel() {
        BaseMod.subscribe(this);
    }

    private void ensureTexture() {
        if (whitePixel != null) return;
        try {
            Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            px.setColor(Color.WHITE);
            px.fill();
            whitePixel = new Texture(px);
            px.dispose();
        } catch (Exception e) {}
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        ensureTexture();
        if (whitePixel == null) return;

        float y = Y;

        // background panel
        float panelH = 380 * Settings.yScale;
        sb.setColor(BG);
        sb.draw(whitePixel, X - 8, y - panelH, 280 * Settings.xScale, panelH);
        sb.setColor(Color.WHITE);

        y -= 8;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardTitleFont,
            "CrossSpire Lobby", X, y, TITLE_COLOR);
        y -= ROW_H + 4;

        boolean connected = CrossSpireMod.isConnected();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Status: " + (connected ? "Connected" : "Disconnected"),
            X, y, connected ? GREEN : RED);
        y -= ROW_H;

        String pid = CrossSpireMod.playerId;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Player: " + (pid.isEmpty() ? "(none)" : pid.substring(0, 8)), X, y, Color.WHITE);
        y -= ROW_H;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Room: " + ServerPicker.roomCode, X, y, CYAN);
        y -= ROW_H + 4;

        // --- Connect / Disconnect buttons ---
        if (!connected) {
            drawButton(sb, "Connect to Relay", X, y, BTN_W, BTN_H, BTN_GREEN);
        } else {
            drawButton(sb, "Disconnect", X, y, BTN_W, BTN_H, BTN_RED);
        }
        y -= BTN_H + 4;

        // --- Character selection ---
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Character:", X, y, Color.WHITE);
        y -= ROW_H;
        String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        float cx = X;
        for (String ch : chars) {
            Color btnColor = ch.equals(selectedCharacter) ? BTN_ORANGE : BTN_BG;
            float charW = FontHelper.getWidth(FontHelper.tipBodyFont, ch, 1.0F) + 20;
            drawButton(sb, ch, cx, y, charW, BTN_H - 4, btnColor);
            cx += charW + 6;
            if (cx > X + 260 * Settings.xScale) break;
        }
        y -= BTN_H + 2;

        // --- Ready / Start buttons ---
        drawButton(sb, "Ready", X, y, BTN_W / 2 - 4, BTN_H, BTN_ORANGE);
        drawButton(sb, "Start Game", X + BTN_W / 2 + 4, y, BTN_W / 2 - 4, BTN_H, BTN_GREEN);
        y -= BTN_H + 6;

        // --- Remote player list ---
        int count = RemotePlayerRegistry.count();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Remote (" + count + "):", X, y, CYAN);
        y -= ROW_H;
        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            String info = "  " + rp.playerId.substring(0, 8);
            if (rp.characterClass != null && !rp.characterClass.isEmpty()) info += " " + rp.characterClass;
            info += " " + rp.hp + "/" + rp.maxHp;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, info, X, y, Color.WHITE);
            y -= ROW_H;
        }
    }

    private void drawButton(SpriteBatch sb, String text, float x, float y, float w, float h, Color color) {
        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        boolean hover = mx >= x && mx <= x + w && my <= y && my >= y - h;

        Color drawColor = hover ? (InputHelper.isMouseDown ? BTN_PRESS : BTN_HOVER) : color;
        sb.setColor(drawColor);
        sb.draw(whitePixel, x, y - h, w, h);
        sb.setColor(Color.WHITE);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            text, x + 6, y - 6, WHITE);
    }

    @Override
    public void receivePostUpdate() {
        if (!InputHelper.justClickedLeft) return;

        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        float y = Y;

        // skip title + status + player + room (4 rows + 4 padding)
        y -= 8 + ROW_H + 4 + ROW_H * 3 + 4;

        // Connect / Disconnect button
        if (clickInRect(mx, my, X, y, BTN_W, BTN_H)) {
            boolean connected = CrossSpireMod.isConnected();
            if (!connected) {
                CrossSpireMod.connect();
            } else {
                CrossSpireMod.disconnect();
            }
            return;
        }
        y -= BTN_H + 4;

        // "Character:" row
        y -= ROW_H;

        // Character selection buttons
        String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        float cx = X;
        for (String ch : chars) {
            float charW = FontHelper.getWidth(FontHelper.tipBodyFont, ch, 1.0F) + 20;
            if (clickInRect(mx, my, cx, y, charW, BTN_H - 4)) {
                selectedCharacter = ch;
                return;
            }
            cx += charW + 6;
        }
        y -= BTN_H + 2;

        // Ready button
        if (clickInRect(mx, my, X, y, BTN_W / 2 - 4, BTN_H)) {
            CrossSpireMod.lobbyState.markLocalReady(selectedCharacter);
            return;
        }

        // Start Game button
        if (clickInRect(mx, my, X + BTN_W / 2 + 4, y, BTN_W / 2 - 4, BTN_H)) {
            String seed = String.valueOf(System.currentTimeMillis() % 900000 + 100000);
            crossspire.remote.GameStarter.start(selectedCharacter, seed);
            return;
        }
    }

    private boolean clickInRect(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my <= y && my >= y - h;
    }
}
