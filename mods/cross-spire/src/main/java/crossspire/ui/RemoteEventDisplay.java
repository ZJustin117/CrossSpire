package crossspire.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.network.EventMessageSender;

public class RemoteEventDisplay {

    private static boolean visible = false;
    private static String eventId = "";
    private static String description = "";
    private static String[] optionTexts = new String[0];
    private static boolean[] optionDisabled = new boolean[0];

    private static final Color BG = new Color(0.0F, 0.0F, 0.1F, 0.85F);
    private static final Color BTN_BG = new Color(0.2F, 0.3F, 0.4F, 0.85F);
    private static final Color BTN_HOVER = new Color(0.3F, 0.55F, 0.65F, 0.9F);
    private static final Color BTN_DISABLED = new Color(0.15F, 0.15F, 0.15F, 0.6F);
    private static final Color TITLE = Color.GOLD;
    private static final Color WHITE = Color.WHITE;

    public static void show(String eventId, String desc, JsonArray options) {
        RemoteEventDisplay.eventId = eventId;
        RemoteEventDisplay.description = desc;
        int count = options.size();
        optionTexts = new String[count];
        optionDisabled = new boolean[count];
        for (int i = 0; i < count; i++) {
            JsonObject opt = options.get(i).getAsJsonObject();
            optionTexts[i] = opt.has("text") ? opt.get("text").getAsString() : "Option " + i;
            optionDisabled[i] = opt.has("disabled") ? opt.get("disabled").getAsBoolean() : false;
        }
        visible = true;
    }

    public static void hide() {
        visible = false;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void render(SpriteBatch sb, Texture whitePixel) {
        if (!visible) return;
        if (FontHelper.tipBodyFont == null) return;

        float scale = Settings.xScale;
        float panelW = 380F * scale;
        float panelX = (Settings.WIDTH - panelW) / 2F;
        float panelH = 60F + 32F * optionTexts.length * scale;
        float panelY = Settings.HEIGHT / 2F + 100F * scale;

        sb.setColor(BG);
        sb.draw(whitePixel, panelX, panelY - panelH, panelW, panelH);

        float y = panelY - 14F * scale;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            eventId, panelX + 10F * scale, y, TITLE);
        y -= 22F * scale;

        if (!description.isEmpty()) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                description, panelX + 10F * scale, y, WHITE);
            y -= 24F * scale;
        }
        y -= 4F * scale;

        float btnW = panelW - 20F * scale;
        float btnH = 26F * scale;
        float btnY = y;

        for (int i = 0; i < optionTexts.length; i++) {
            Color c = optionDisabled[i] ? BTN_DISABLED : BTN_BG;
            float by = y;
            sb.setColor(c);
            sb.draw(whitePixel, panelX + 10F * scale, by - btnH, btnW, btnH);

            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                optionTexts[i], panelX + 14F * scale, by - 6F * scale, WHITE);
            y -= btnH + 4F * scale;
        }
    }

    public static void update() {
        if (!visible) return;
        if (!InputHelper.justClickedLeft) return;

        float scale = Settings.xScale;
        float panelW = 380F * scale;
        float panelX = (Settings.WIDTH - panelW) / 2F;
        float panelH = 60F + 32F * optionTexts.length * scale;
        float panelY = Settings.HEIGHT / 2F + 100F * scale;
        float btnW = panelW - 20F * scale;
        float btnH = 26F * scale;
        float btnY = panelY - 38F * scale - 24F * scale;

        for (int i = 0; i < optionTexts.length; i++) {
            float by = btnY - i * (btnH + 4F * scale);
            if (InputHelper.mX > panelX + 10F * scale && InputHelper.mX < panelX + 10F * scale + btnW
                && InputHelper.mY < by && InputHelper.mY > by - btnH) {
                if (optionDisabled[i]) return;
                String msg = EventMessageSender.buildEventSelect(
                    CrossSpireMod.playerId, i);
                CrossSpireMod.send(msg);
                hide();
            }
        }
    }
}
