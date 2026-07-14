package crossspire.ui;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class RemoteStatsOverlay {

    private static final Color HP_BAR_BG    = new Color(0.0F, 0.0F, 0.0F, 0.6F);
    private static final Color HP_GREEN     = new Color(0.0F, 0.8F, 0.2F, 0.9F);
    private static final Color HP_YELLOW    = new Color(0.9F, 0.8F, 0.1F, 0.9F);
    private static final Color HP_RED       = new Color(0.8F, 0.1F, 0.1F, 0.9F);
    private static final Color BLOCK_COLOR  = new Color(0.3F, 0.6F, 0.9F, 1.0F);
    private static final Color BUFF_BG      = new Color(0.15F, 0.15F, 0.25F, 0.8F);
    private static final Color BUFF_TEXT    = new Color(0.7F, 0.9F, 0.3F, 1.0F);
    private static final Color DEBUFF_TEXT  = new Color(0.9F, 0.3F, 0.3F, 1.0F);
    private static final Color CHAR_COLOR   = new Color(0.9F, 0.8F, 0.2F, 1.0F);

    public static void renderStatic(SpriteBatch sb, Texture whitePixel) {
        if (com.megacrit.cardcrawl.helpers.FontHelper.tipBodyFont == null) return;
        int count = RemotePlayerRegistry.count();
        if (count == 0) return;

        float panelX = Settings.WIDTH - 160.0F * Settings.xScale;
        float panelY = Settings.HEIGHT - 40.0F * Settings.yScale;
        float hpBarW = 120.0F * Settings.xScale;
        float hpBarH = 10.0F * Settings.yScale;
        float lineH = 20.0F * Settings.yScale;
        float iconH = 14.0F * Settings.yScale;

        float y = panelY;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardTitleFont,
            "Remote", panelX, y, Color.YELLOW);
        y -= lineH;

        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            y = drawPlayerRow(sb, whitePixel, rp, panelX, y, hpBarW, hpBarH, lineH, iconH);
        }
    }

    private static float drawPlayerRow(SpriteBatch sb, Texture px,
            RemotePlayerState rp, float x, float y,
            float barW, float barH, float lineH, float iconH) {
        String label = rp.playerId.substring(0, 8);
        if (rp.characterClass != null && !rp.characterClass.isEmpty()) {
            label += " (" + rp.characterClass + ")";
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, label, x, y, CHAR_COLOR);
        y -= lineH;

        float ratio = rp.maxHp > 0 ? (float) rp.hp / rp.maxHp : 0;
        ratio = Math.max(0, Math.min(1, ratio));

        sb.setColor(HP_BAR_BG);
        sb.draw(px, x, y - barH, barW, barH);
        sb.setColor(Color.WHITE);

        Color barColor = ratio > 0.5F ? HP_GREEN : ratio > 0.25F ? HP_YELLOW : HP_RED;
        sb.setColor(barColor);
        sb.draw(px, x + 1, y - barH + 1, (barW - 2) * ratio, barH - 2);
        sb.setColor(Color.WHITE);

        String hpText = rp.hp + "/" + rp.maxHp;
        Color hpColor = barColor;
        if (rp.block > 0) {
            hpText += " B:" + rp.block;
            hpColor = BLOCK_COLOR;
        }
        if (rp.energy > 0) {
            hpText += " E:" + rp.energy;
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont, hpText, x + barW + 4, y, hpColor);
        y -= barH + 4;

        if (rp.powers != null && rp.powers.length > 0) {
            y = drawBuffRow(sb, px, rp, x, y, iconH);
        }
        return y - 4;
    }

    private static float drawBuffRow(SpriteBatch sb, Texture px,
            RemotePlayerState rp, float x, float y, float iconH) {
        float bufX = x;
        int maxShow = Math.min(rp.powers.length, 6);
        float pad = 3;

        for (int i = 0; i < maxShow; i++) {
            String powerName = rp.powers[i];
            int amt = i < rp.powerAmounts.length ? rp.powerAmounts[i] : 1;
            String text = iconName(powerName) + (amt > 1 ? "x" + amt : "");

            float textW = FontHelper.getWidth(FontHelper.tipBodyFont, text, 1.0F) + pad * 4;

            boolean isDebuff = powerName.toLowerCase().contains("vulnerable")
                || powerName.toLowerCase().contains("weak")
                || powerName.toLowerCase().contains("frail");
            Color textColor = isDebuff ? DEBUFF_TEXT : BUFF_TEXT;

            sb.setColor(BUFF_BG);
            sb.draw(px, bufX, y - iconH - 2, textW, iconH + 4);
            sb.setColor(Color.WHITE);

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                text, bufX + pad, y - pad / 2, textColor);

            bufX += textW + 4;
            if (bufX > x + 200) break;
        }
        return y - iconH - 4;
    }

    private static String iconName(String powerId) {
        if (powerId == null || powerId.isEmpty()) return "?";
        if (powerId.length() <= 4) return powerId;
        StringBuilder sb = new StringBuilder();
        for (char c : powerId.toCharArray()) {
            if (sb.length() > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        String result = sb.toString().trim();
        String[] words = result.split(" ");
        if (words.length > 1) {
            StringBuilder abbr = new StringBuilder();
            for (String w : words) {
                if (!w.isEmpty()) abbr.append(Character.toUpperCase(w.charAt(0)));
            }
            return abbr.length() > 0 ? abbr.toString() : result.substring(0, Math.min(3, result.length()));
        }
        return result.length() > 4 ? result.substring(0, 4) : result;
    }
}
