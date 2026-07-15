package crossspire.network;

import crossspire.CrossSpireMod;

public class BroadcastManager {

    public static void broadcast(String message) {
        CrossSpireMod.send(message);
    }
}
