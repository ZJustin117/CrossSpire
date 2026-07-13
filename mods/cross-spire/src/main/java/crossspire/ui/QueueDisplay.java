package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.helpers.FontHelper;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class QueueDisplay {

    private static final List<Protocol.QueuePacket> displayQueue = Collections.synchronizedList(new ArrayList<Protocol.QueuePacket>());

    public static void onPacket(Protocol.QueuePacket pkt) {
        synchronized (displayQueue) {
            for (int i = 0; i < displayQueue.size(); i++) {
                if (displayQueue.get(i).packetId.equals(pkt.packetId)) return;
            }
            displayQueue.add(pkt);
            displayQueue.sort(new Comparator<Protocol.QueuePacket>() {
                @Override
                public int compare(Protocol.QueuePacket a, Protocol.QueuePacket b) {
                    if (a.timestamp != b.timestamp) return Long.compare(a.timestamp, b.timestamp);
                    return a.senderId.compareTo(b.senderId);
                }
            });
        }
        BaseMod.logger.info("QueueDisplay added: " + pkt.packetId);
    }

    public static void onComplete(String packetId) {
        synchronized (displayQueue) {
            for (int i = 0; i < displayQueue.size(); i++) {
                if (displayQueue.get(i).packetId.equals(packetId)) {
                    displayQueue.remove(i);
                    return;
                }
            }
        }
        BaseMod.logger.info("QueueDisplay complete: " + packetId);
    }

    public static void show() {
        synchronized (displayQueue) {
            if (displayQueue.isEmpty()) {
                DevConsole.log("Queue: (empty)");
                return;
            }
            DevConsole.log("Queue (" + displayQueue.size() + "):");
            int idx = 0;
            for (Protocol.QueuePacket p : displayQueue) {
                boolean mine = p.ownerId.equals(CrossSpireMod.playerId);
                String marker = mine ? "[MINE]" : "[REMOTE]";
                String sid = p.senderId.isEmpty() ? "??" : p.senderId.substring(0, 8);
                DevConsole.log(String.format("  %d. %s %s by %s", ++idx, marker, p.cardId, sid));
            }
        }
    }

    public static void render(SpriteBatch sb) {
        synchronized (displayQueue) {
            if (displayQueue.isEmpty()) return;
            int size = displayQueue.size();
            float y = 200;
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "Queue (" + size + ")", 10, y, com.badlogic.gdx.graphics.Color.YELLOW);
            y -= 16;
            int idx = 0;
            for (Protocol.QueuePacket p : displayQueue) {
                if (idx++ >= 10) break;
                boolean mine = p.ownerId.equals(CrossSpireMod.playerId);
                String sid = p.senderId.isEmpty() ? "??" : p.senderId.substring(0, 8);
                String line = (idx) + ". " + (mine ? "[MINE]" : "[   ]") + " " + p.cardId + " by " + sid;
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    line, 10, y, mine ? com.badlogic.gdx.graphics.Color.GREEN : com.badlogic.gdx.graphics.Color.WHITE);
                y -= 14;
            }
        }
    }

    public static int size() {
        synchronized (displayQueue) { return displayQueue.size(); }
    }
}
