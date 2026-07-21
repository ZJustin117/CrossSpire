package crossspire.event;

import crossspire.network.Protocol;

/** Pure decision for whether a party client can open a native event instance. */
public final class NativeEventOpenPlanner {

    public enum Mode {
        NATIVE,
        FALLBACK
    }

    private NativeEventOpenPlanner() {}

    public static Mode mode(Protocol.EventInterfacePayload iface, boolean classPresent, String localHash) {
        if (iface == null || empty(iface.eventClass) || empty(iface.resourceHash)) return Mode.FALLBACK;
        if (!classPresent) return Mode.FALLBACK;
        if (localHash == null || localHash.isEmpty() || !localHash.equals(iface.resourceHash)) {
            return Mode.FALLBACK;
        }
        return Mode.NATIVE;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
