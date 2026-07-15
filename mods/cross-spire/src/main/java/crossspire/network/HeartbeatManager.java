package crossspire.network;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import com.google.gson.JsonObject;

public class HeartbeatManager {

    private static Thread heartbeatThread;
    private static final int INTERVAL_MS = 15000;

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
                        ping.addProperty("seq", 0);
                        CrossSpireMod.send(ping.toString());
                    }
                }
            }
        }, "Heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        BaseMod.logger.info("HeartbeatManager started");
    }

    public static void stop() {
        if (heartbeatThread != null) heartbeatThread.interrupt();
        heartbeatThread = null;
    }
}
