package crossspire.network;

import crossspire.CrossSpireMod;

public class BroadcastManager {

    public static void broadcast(String message) {
        if (CrossSpireMod.p2pManager != null) {
            CrossSpireMod.p2pManager.broadcast(message);
        }
        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(message);
        }
    }
}
