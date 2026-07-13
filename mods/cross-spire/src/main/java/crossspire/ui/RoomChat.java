package crossspire.ui;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.helpers.FontHelper;
import java.util.ArrayList;
import java.util.List;

public class RoomChat {

    private static final List<String> messages = new ArrayList<String>();
    private static final int MAX_MESSAGES = 20;

    public static void addMessage(String playerId, String text) {
        String msg = (playerId.length() > 8 ? playerId.substring(0, 8) : playerId) + ": " + text;
        messages.add(msg);
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
        BaseMod.logger.info("RoomChat: " + msg);
    }

    public static void render(SpriteBatch sb) {
        if (messages.isEmpty()) return;
        float y = 40;
        for (int i = messages.size() - 1; i >= 0; i--) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                messages.get(i), 10, y, com.badlogic.gdx.graphics.Color.WHITE);
            y -= 14;
        }
    }
}
