package crossspire.event;

import java.util.HashMap;
import java.util.Map;

/** Tracks whether a party event instance was opened natively or as fallback UI. */
public final class EventOpenModeRegistry {

    public static final String NATIVE = "native";
    public static final String FALLBACK = "fallback";

    private static final Map<String, String> modes = new HashMap<String, String>();

    private EventOpenModeRegistry() {}

    public static synchronized void mark(String eventInstanceId, String mode) {
        if (eventInstanceId == null || eventInstanceId.isEmpty() || mode == null) return;
        modes.put(eventInstanceId, mode);
    }

    public static synchronized String mode(String eventInstanceId) {
        return eventInstanceId == null ? null : modes.get(eventInstanceId);
    }

    public static synchronized boolean isFallback(String eventInstanceId) {
        return FALLBACK.equals(mode(eventInstanceId));
    }

    public static synchronized void clear(String eventInstanceId) {
        if (eventInstanceId != null) modes.remove(eventInstanceId);
    }
}
