package crossspire.ui;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import crossspire.CrossSpireMod;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class RoomPanel {

    private static final Color BG         = new Color(0.0F, 0.0F, 0.15F, 0.8F);
    private static final Color BTN_BG     = new Color(0.2F, 0.3F, 0.4F, 0.85F);
    private static final Color BTN_HOVER  = new Color(0.3F, 0.55F, 0.65F, 0.9F);
    private static final Color BTN_PRESS  = new Color(0.5F, 0.75F, 0.85F, 1.0F);
    private static final Color BTN_GREEN  = new Color(0.15F, 0.55F, 0.2F, 0.85F);
    private static final Color BTN_ORANGE = new Color(0.65F, 0.4F, 0.1F, 0.85F);
    private static final Color BTN_RED    = new Color(0.6F, 0.15F, 0.15F, 0.85F);
    private static final Color BTN_GREY   = new Color(0.25F, 0.25F, 0.25F, 0.7F);
    private static final Color TITLE_C    = new Color(0.95F, 0.8F, 0.05F, 1.0F);
    private static final Color WHITE_C    = Color.WHITE;
    private static final Color CYAN_C     = Color.CYAN;
    private static final Color GOLD_C     = Color.GOLD;
    private static final Color RED_C      = new Color(0.85F, 0.2F, 0.2F, 1.0F);

    private static String selectedCharacter = "IRONCLAD";
    private static boolean isReady = false;

    private static float connBtnY, charBtnY, readyBtnY, playBtnY;

    public static String getSelectedCharacter() { return selectedCharacter; }

    public static void renderStatic(SpriteBatch sb, Texture whitePixel) {
        float scale = Settings.xScale;
        float panelX = 30F * scale;
        float panelW = 295F * scale;
        float panelH = 285F * scale;
        float panelY = Settings.HEIGHT - 30F * scale;
        float lineH = 22F * scale;

        sb.setColor(BG);
        sb.draw(whitePixel, panelX - 4, panelY - panelH, panelW + 8, panelH);

        float y = panelY - 10F * scale;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "CrossSpire Lobby", panelX + 6, y, TITLE_C);
        y -= lineH * 1.2F;

        boolean connected = CrossSpireMod.isConnected();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            connected ? "[ Connected ]" : "[ Disconnected ]", panelX + 6, y,
            connected ? Color.GREEN : RED_C);
        y -= lineH;

        String pid = CrossSpireMod.playerId;
        if (!pid.isEmpty()) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "ID: " + pid.substring(0, 8), panelX + 6, y, WHITE_C);
            y -= lineH;
        }

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Room: " + ServerPicker.roomCode, panelX + 6, y, CYAN_C);
        y -= lineH;

        if (CrossSpireMod.syncedSeed != null) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Seed: " + CrossSpireMod.syncedSeed, panelX + 6, y, GOLD_C);
            y -= lineH;
        }
        y -= 4;

        float btnW = 145F * scale;
        float btnH = 26F * scale;
        connBtnY = y;
        renderButton(sb, whitePixel, panelX + 6, y, btnW, btnH,
            connected ? "Disconnect" : "Connect to Relay", connected ? BTN_RED : BTN_GREEN);
        y -= btnH + 2;

        String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        charBtnY = y;
        float charX = panelX + 6;
        for (String c : chars) {
            float cw = FontHelper.getSmartWidth(FontHelper.tipBodyFont, c, 200, 0) + 12;
            renderButton(sb, whitePixel, charX, y, cw, btnH, c,
                selectedCharacter.equals(c) ? BTN_ORANGE : BTN_BG);
            charX += cw + 4;
        }
        y -= btnH + 2;

        readyBtnY = y;
        renderButton(sb, whitePixel, panelX + 6, y, btnW, btnH,
            isReady ? "[ READY ]" : "Ready", isReady ? BTN_GREY : BTN_ORANGE);
        y -= btnH + 2;

        playBtnY = y;
        boolean canPlay = CrossSpireMod.syncedSeed != null;
        renderButton(sb, whitePixel, panelX + 6, y, btnW, btnH,
            "Play", canPlay ? BTN_GREEN : BTN_GREY);
        y -= btnH + 6;

        int rc = RemotePlayerRegistry.count();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Remote (" + rc + ")", panelX + 6, y, CYAN_C);
        y -= lineH;
        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            String cls = rp.characterClass != null && !rp.characterClass.isEmpty()
                ? rp.characterClass : "?";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                rp.playerId.substring(0, 8) + " " + cls + " HP:" + rp.hp + "/" + rp.maxHp,
                panelX + 6, y, WHITE_C);
            y -= lineH;
            if (y < panelY - panelH + 10) break;
        }
    }

    public static void updateStatic() {
        float scale = Settings.xScale;
        float panelX = 30F * scale;
        float btnW = 145F * scale;
        float btnH = 26F * scale;

        if (InputHelper.justClickedLeft) {
            int mx = InputHelper.mX;
            int my = InputHelper.mY;

            if (hitTest(panelX + 6, connBtnY, btnW, btnH, mx, my)) {
                if (CrossSpireMod.isConnected()) {
                    CrossSpireMod.disconnect();
                } else {
                    CrossSpireMod.connect();
                }
                return;
            }

            if (hitTest(panelX + 6, charBtnY, btnW * 2, btnH, mx, my)) {
                String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
                int idx = -1;
                float cx = panelX + 6;
                for (int i = 0; i < chars.length; i++) {
                    float cw = FontHelper.getSmartWidth(FontHelper.tipBodyFont, chars[i], 200, 0) + 12;
                    if (hitTest(cx, charBtnY, cw, btnH, mx, my)) { idx = i; break; }
                    cx += cw + 4;
                }
                if (idx >= 0) selectedCharacter = chars[idx];
                return;
            }

            if (!isReady && hitTest(panelX + 6, readyBtnY, btnW, btnH, mx, my)) {
                isReady = true;
                CrossSpireMod.lobbyState.markLocalReady(selectedCharacter);
                return;
            }

            if (hitTest(panelX + 6, playBtnY, btnW, btnH, mx, my)
                && CrossSpireMod.syncedSeed != null) {
                Gdx.app.postRunnable(new Runnable() {
                    @Override public void run() {
                        crossspire.remote.GameStarter.start(selectedCharacter,
                            CrossSpireMod.syncedSeed);
                    }
                });
            }
        }
    }

    private static boolean hitTest(float bx, float by, float bw, float bh, int mx, int my) {
        return mx >= bx && mx <= bx + bw && my <= by && my >= by - bh;
    }

    private static void renderButton(SpriteBatch sb, Texture px,
            float x, float y, float w, float h, String text, Color colour) {
        float mx = InputHelper.mX, my = InputHelper.mY;
        Color c = colour;
        if (hitTest(x, y, w, h, (int) mx, (int) my)) {
            c = InputHelper.isMouseDown ? BTN_PRESS : BTN_HOVER;
        }
        sb.setColor(c);
        sb.draw(px, x, y - h, w, h);
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, text,
            x + w / 2, y - h / 2 + 4, WHITE_C);
    }
}
