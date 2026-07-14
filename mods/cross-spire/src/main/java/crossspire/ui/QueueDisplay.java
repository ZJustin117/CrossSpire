package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class QueueDisplay {

    private static final List<Protocol.QueueEntry> entries = Collections.synchronizedList(new ArrayList<Protocol.QueueEntry>());
    private static boolean endTurnAllowed = false;

    public static void onUpdate(Protocol.QueueEntry[] updatedEntries) {
        synchronized (entries) {
            entries.clear();
            if (updatedEntries != null) {
                for (Protocol.QueueEntry e : updatedEntries) {
                    entries.add(e);
                }
            }
        }
    }

    public static void onQueueEmpty() {
        endTurnAllowed = true;
        BaseMod.logger.info("QueueDisplay queue_empty — end turn allowed");
    }

    public static boolean isEndTurnAllowed() {
        return endTurnAllowed;
    }

    public static void resetEndTurn() {
        endTurnAllowed = false;
    }

    public static void show() {
        synchronized (entries) {
            if (entries.isEmpty()) {
                DevConsole.log("Queue: (empty)");
                return;
            }
            DevConsole.log("Queue (" + entries.size() + "):");
            int idx = 0;
            for (Protocol.QueueEntry e : entries) {
                boolean mine = e.ownerId != null && e.ownerId.equals(CrossSpireMod.playerId);
                String marker = mine ? "[MINE]" : "[REMOTE]";
                String sid = e.senderId != null && !e.senderId.isEmpty() ? e.senderId.substring(0, 8) : "??";
                String status = "pending".equals(e.status) ? "[WAIT]"
                    : "executing".equals(e.status) ? "[EXEC]"
                    : "[DONE]";
                DevConsole.log(String.format("  %d. %s %s %s by %s", ++idx, status, marker, e.cardId, sid));
            }
        }
    }

    public static void render(SpriteBatch sb) {
        synchronized (entries) {
            if (entries.isEmpty()) return;
            float y = 200;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Queue (" + entries.size() + ")", 10, y, Color.YELLOW);
            y -= 16;
            int idx = 0;
            for (Protocol.QueueEntry e : entries) {
                if (idx >= 10) break;
                boolean mine = e.ownerId != null && e.ownerId.equals(CrossSpireMod.playerId);
                boolean executing = "executing".equals(e.status);
                String sid = e.senderId != null && !e.senderId.isEmpty() ? e.senderId.substring(0, 8) : "??";

                String status = executing ? "[EXEC]" : mine ? "[MINE]" : "[WAIT]";
                int pos = mine ? minePosition() : -1;
                String posStr = mine && pos > 0 ? " (pos " + pos + "/" + entries.size() + ")" : "";
                String line = (idx + 1) + ". " + status + " " + e.cardId + " by " + sid + posStr;

                Color colour;
                if (executing) {
                    colour = new Color(1.0F, 0.85F, 0.1F, 1.0F);
                } else if (mine) {
                    colour = Color.GREEN;
                } else {
                    colour = Color.WHITE;
                }

                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    line, 10, y, colour);
                y -= 14;
                idx++;
            }
        }
        if (endTurnAllowed) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "TURN OK", 10, 120, Color.GREEN);
        }
    }

    private static int minePosition() {
        int pos = 0;
        for (Protocol.QueueEntry e : entries) {
            pos++;
            if (e.ownerId != null && e.ownerId.equals(CrossSpireMod.playerId)) return pos;
        }
        return -1;
    }

    public static int size() {
        synchronized (entries) { return entries.size(); }
    }

    public static boolean isEmpty() {
        return size() == 0;
    }
}
