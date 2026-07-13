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

    private static final Color BG         = new Color(0.0F, 0.0F, 0.15F, 0.8F);
    private static final Color BTN_BG     = new Color(0.2F, 0.3F, 0.4F, 0.85F);
    private static final Color BTN_HOVER  = new Color(0.3F, 0.55F, 0.65F, 0.9F);
    private static final Color BTN_PRESS  = new Color(0.5F, 0.75F, 0.85F, 1.0F);
    private static final Color BTN_GREEN  = new Color(0.15F, 0.55F, 0.2F, 0.85F);
    private static final Color BTN_ORANGE = new Color(0.65F, 0.4F, 0.1F, 0.85F);
    private static final Color BTN_RED    = new Color(0.6F, 0.15F, 0.15F, 0.85F);
    private static final Color TITLE_C    = new Color(0.95F, 0.8F, 0.05F, 1.0F);
    private static final Color WHITE_C    = Color.WHITE;
    private static final Color CYAN_C     = new Color(0.3F, 0.85F, 0.9F, 1.0F);
    private static final Color GREEN_C    = new Color(0.2F, 0.85F, 0.2F, 1.0F);
    private static final Color RED_C      = new Color(0.85F, 0.2F, 0.2F, 1.0F);

    private String selectedCharacter = "IRONCLAD";

    public RoomPanel() {
        BaseMod.subscribe(this);
    }

    // Click target positions — set during render, consumed during update
    private float connBtnY, charBtnY, readyBtnY;

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

        float sx = Math.max(Settings.xScale, 1);
        float sy = Math.max(Settings.yScale, 1);

        float x   = 30.0F * sx;
        float y   = Settings.HEIGHT - 30.0F * sy;
        float rh  = 22.0F * sy;
        float bw  = 145.0F * sx;
        float bh  = 26.0F * sy;

        float panelW = 295.0F * sx;
        float panelH = 270.0F * sy;
        sb.setColor(BG);
        sb.draw(whitePixel, x - 12, y + 12 - panelH, panelW, panelH);
        sb.setColor(Color.WHITE);

        y -= 4;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardTitleFont,
            "CrossSpire Lobby", x, y, TITLE_C);
        y -= rh + 6;

        boolean connected = CrossSpireMod.isConnected();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            connected ? "[ Connected ]" : "[ Disconnected ]", x, y, connected ? GREEN_C : RED_C);
        y -= rh;

        String pid = CrossSpireMod.playerId;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Player: " + (pid.isEmpty() ? "(none)" : (pid.length() >= 8 ? pid.substring(0, 8) : pid)), x, y, WHITE_C);
        y -= rh;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Room: " + ServerPicker.roomCode, x, y, CYAN_C);
        y -= rh + 4;

        // store button Y positions for click detection
        this.connBtnY = y;
        drawBtn(sb, connected ? "Disconnect" : "Connect to Relay", x, y, bw, bh, connected ? BTN_RED : BTN_GREEN);
        y -= bh + 4;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, "Character:", x, y, WHITE_C);
        y -= rh;
        this.charBtnY = y;
        String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        float cx = x;
        for (String ch : chars) {
            boolean sel = ch.equals(selectedCharacter);
            float cw = FontHelper.getWidth(FontHelper.tipBodyFont, ch, 1.0F) * 1.5F + 14;
            drawBtn(sb, ch, cx, y, cw, bh - 3, sel ? BTN_ORANGE : BTN_BG);
            cx += cw + 4;
        }
        y -= bh - 2 + 4;

        this.readyBtnY = y;
        drawBtn(sb, "Ready", x, y, bw, bh, BTN_ORANGE);
        y -= bh + 8;

        int count = RemotePlayerRegistry.count();
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, "Remote (" + count + ")", x, y, CYAN_C);
        y -= rh;
        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            String info = rp.playerId.substring(0, 8);
            if (rp.characterClass != null && !rp.characterClass.isEmpty()) info += " " + rp.characterClass;
            info += " HP:" + rp.hp + "/" + rp.maxHp;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, info, x + 8, y, WHITE_C);
            y -= rh;
        }

        sb.setColor(Color.WHITE);
    }

    private void drawBtn(SpriteBatch sb, String text, float x, float y, float w, float h, Color bc) {
        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        boolean hover = mx >= x && mx <= x + w && my <= y && my >= y - h;
        Color c = hover ? (InputHelper.isMouseDown ? BTN_PRESS : BTN_HOVER) : bc;

        sb.setColor(c);
        sb.draw(whitePixel, x, y - h, w, h);
        sb.setColor(Color.WHITE);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, text, x + 6, y - 5, Color.WHITE);
    }

    @Override
    public void receivePostUpdate() {
        // Handle pending game start (joiner received stage_sync from host)
        if (CrossSpireMod.pendingStartSeed != null) {
            String sd = CrossSpireMod.pendingStartSeed;
            String cn = CrossSpireMod.lobbyState.getMyCharacter();
            CrossSpireMod.pendingStartSeed = null;
            BaseMod.logger.info("RoomPanel deferred start: " + cn + " seed=" + sd);
            String usedSeed = crossspire.remote.GameStarter.start(cn, sd);
            if (usedSeed != null) {
                CrossSpireMod.lastStartedChar = cn;
                CrossSpireMod.lastStartedSeed = usedSeed;
                CrossSpireMod.startedGame = true;
            }
            return;
        }

        if (!InputHelper.justClickedLeft) return;

        float mx = InputHelper.mX;
        float my = InputHelper.mY;
        float x  = 30.0F * Math.max(Settings.xScale, 1);
        float bw = 145.0F * Math.max(Settings.xScale, 1);
        float bh = 26.0F * Math.max(Settings.yScale, 1);

        // Use stored Y positions from render
        if (connBtnY > 0 && inRect(mx, my, x, connBtnY, bw, bh)) {
            if (CrossSpireMod.isConnected()) CrossSpireMod.disconnect();
            else CrossSpireMod.connect();
            return;
        }

        if (charBtnY > 0) {
            String[] chars = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
            float cx = x;
            for (String ch : chars) {
                float cw = FontHelper.getWidth(FontHelper.tipBodyFont, ch, 1.0F) * 1.5F + 14;
                if (inRect(mx, my, cx, charBtnY, cw, bh - 3)) { selectedCharacter = ch; return; }
                cx += cw + 4;
            }
        }

        if (readyBtnY > 0 && inRect(mx, my, x, readyBtnY, bw, bh)) {
            CrossSpireMod.lobbyState.markLocalReady(selectedCharacter);
        }
    }

    private boolean inRect(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my <= y && my >= y - h;
    }
}
