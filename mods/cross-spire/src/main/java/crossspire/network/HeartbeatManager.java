package crossspire.network;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeartbeatManager {

    private static Thread heartbeatThread;
    private static Thread timeoutThread;
    private static final int INTERVAL_MS = 15000;
    private static final int TIMEOUT_MS = 30000;

    private static final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    private static OnPeerTimeoutListener timeoutListener = null;

    public interface OnPeerTimeoutListener {
        void onPeerTimeout(String peerId);
    }

    public static void setOnPeerTimeoutListener(OnPeerTimeoutListener listener) {
        timeoutListener = listener;
    }

    public static void start() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) return;
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try { Thread.sleep(INTERVAL_MS); } catch (InterruptedException e) { break; }
                    if (CrossSpireMod.isConnected()) {
                        JsonObject ping = new JsonObject();
                        ping.addProperty("type", "ping");
                        ping.addProperty("seq", CrossSpireMod.nextSeq());
                        CrossSpireMod.send(ping.toString());
                    }
                }
            }
        }, "Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        timeoutThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
                    long now = System.currentTimeMillis();
                    for (Map.Entry<String, Long> e : peerLastSeen.entrySet()) {
                        if (now - e.getValue() > TIMEOUT_MS) {
                            BaseMod.logger.info("HeartbeatManager timeout: " + e.getKey().substring(0, 8));
                            peerLastSeen.remove(e.getKey());
                            if (timeoutListener != null) {
                                timeoutListener.onPeerTimeout(e.getKey());
                            }
                        }
                    }
                }
            }
        }, "Heartbeat-Timeout");
        timeoutThread.setDaemon(true);
        timeoutThread.start();

        BaseMod.logger.info("HeartbeatManager started");
    }

    public static void stop() {
        if (heartbeatThread != null) heartbeatThread.interrupt();
        if (timeoutThread != null) timeoutThread.interrupt();
        heartbeatThread = null;
        timeoutThread = null;
        peerLastSeen.clear();
    }

    public static void handlePong(String peerId) {
        peerLastSeen.put(peerId, System.currentTimeMillis());
    }

    public static long getLastSeen(String peerId) {
        Long ts = peerLastSeen.get(peerId);
        return ts != null ? ts : 0L;
    }

    public static boolean isTimedOut(String peerId, long timeoutMs) {
        Long ts = peerLastSeen.get(peerId);
        if (ts == null) return false;
        return System.currentTimeMillis() - ts > timeoutMs;
    }

    static void testSetLastSeen(String peerId, long timestamp) {
        peerLastSeen.put(peerId, timestamp);
    }

    static void resetForTest() {
        peerLastSeen.clear();
        heartbeatThread = null;
        timeoutThread = null;
        timeoutListener = null;
    }
}
