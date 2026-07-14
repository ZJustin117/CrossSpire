package crossspire.ui;

import basemod.BaseMod;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import crossspire.CrossSpireMod;

public class CrossSpireHUD implements PostRenderSubscriber, PostUpdateSubscriber {

    private boolean showLobby     = true;
    private boolean showCombatHUD = true;
    private boolean showQueue     = false;
    private boolean showChat      = false;

    private Texture whitePixel;
    private boolean inCombat;

    private static final Color TAB_BG        = new Color(0.05F, 0.05F, 0.1F, 0.7F);
    private static final Color TAB_SEL_BG    = new Color(0.15F, 0.35F, 0.45F, 0.9F);
    private static final Color TAB_OFF_BG    = new Color(0.08F, 0.08F, 0.12F, 0.5F);
    private static final Color TAB_ON_TXT    = new Color(0.95F, 0.95F, 0.3F, 1.0F);
    private static final Color TAB_OFF_TXT   = new Color(0.45F, 0.45F, 0.45F, 0.7F);
    private static final Color STAT_BG       = new Color(0.0F, 0.0F, 0.0F, 0.4F);
    private static final Color STAT_TXT      = Color.LIGHT_GRAY;

    public CrossSpireHUD() {
        BaseMod.subscribe(this);
    }

    private void ensureTexture() {
        if (whitePixel != null) return;
        try {
            Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            px.setColor(1, 1, 1, 1);
            px.fill();
            whitePixel = new Texture(px);
            px.dispose();
        } catch (Exception e) {
            BaseMod.logger.error("CrossSpireHUD texture: " + e.getMessage());
        }
    }

    @Override
    public void receivePostUpdate() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) showLobby     = !showLobby;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) showCombatHUD = !showCombatHUD;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) showQueue     = !showQueue;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) showChat      = !showChat;

        try {
            inCombat = AbstractDungeon.player != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
        } catch (Exception e) {
            inCombat = false;
        }

        if (showLobby) RoomPanel.updateStatic();
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        ensureTexture();
        if (whitePixel == null) return;
        try {
            renderStatusBar(sb);
            renderTabBar(sb);

            if (showLobby) RoomPanel.renderStatic(sb, whitePixel);
            if (showCombatHUD && inCombat) RemoteStatsOverlay.renderStatic(sb, whitePixel);
            if (showQueue) QueueDisplay.render(sb);
            if (showChat) RoomChat.render(sb);
        } catch (Exception e) {
            // silently ignore during render
        }
    }

    private void renderTabBar(SpriteBatch sb) {
        float scale = Settings.xScale;
        float tabW = 66F * scale;
        float tabH = 20F * scale;
        float startX = 10F;
        float y = Settings.HEIGHT - 2F;

        sb.setColor(TAB_BG);
        sb.draw(whitePixel, startX - 2, y - tabH - 2,
            (tabW + 2) * 4 + 90F * scale, tabH + 4);
        sb.setColor(Color.WHITE);

        String[] labels = {" F1 Lobby", " F2 Combat", " F3 Queue", " F4 Chat"};
        boolean[] states = {showLobby, showCombatHUD, showQueue, showChat};
        float x = startX;

        for (int i = 0; i < 4; i++) {
            boolean active = states[i];
            sb.setColor(active ? TAB_SEL_BG : TAB_OFF_BG);
            sb.draw(whitePixel, x, y - tabH, tabW, tabH);
            sb.setColor(Color.WHITE);

            Color c = active ? TAB_ON_TXT : TAB_OFF_TXT;
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
                labels[i], x + tabW / 2, y - tabH / 2 + 3, c);
            x += tabW + 2;
        }

        String conn = CrossSpireMod.isConnected() ? "ON" : "OFF";
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "Relay: " + conn, x + 4, y - 5,
            CrossSpireMod.isConnected() ? Color.GREEN : Color.RED);
    }

    private void renderStatusBar(SpriteBatch sb) {
        float barH = 16F;
        sb.setColor(STAT_BG);
        sb.draw(whitePixel, 0, 0, Settings.WIDTH, barH);
        sb.setColor(Color.WHITE);

        StringBuilder text = new StringBuilder("CrossSpire");
        if (CrossSpireMod.isConnected() && !CrossSpireMod.playerId.isEmpty()) {
            text.append(" | ").append(CrossSpireMod.playerId, 0, 8);
        }
        if (inCombat && AbstractDungeon.player != null) {
            text.append(" | Floor ").append(AbstractDungeon.floorNum)
                .append(" | HP ").append(AbstractDungeon.player.currentHealth)
                .append("/").append(AbstractDungeon.player.maxHealth)
                .append(" | Gold ").append(AbstractDungeon.player.gold);
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            text.toString(), 6, barH - 2, STAT_TXT);
    }
}
