package crossspire.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TriggerRegistry {

    private static final Map<String, Map<String, List<Reference<?>>>> registry = new ConcurrentHashMap<>();

    public static void register(String eventType, String resourceKey, Reference<?> ref) {
        registry
            .computeIfAbsent(eventType, k -> new ConcurrentHashMap<String, List<Reference<?>>>())
            .computeIfAbsent(resourceKey, k -> new CopyOnWriteArrayList<Reference<?>>())
            .add(ref);
    }

    public static List<Reference<?>> getTriggers(String eventType, String resourceKey) {
        List<Reference<?>> result = new ArrayList<>();
        Map<String, List<Reference<?>>> eventMap = registry.get(eventType);
        if (eventMap != null) {
            List<Reference<?>> exact = eventMap.get(resourceKey);
            if (exact != null) result.addAll(exact);
            List<Reference<?>> wildcard = eventMap.get("*");
            if (wildcard != null) result.addAll(wildcard);
        }
        Map<String, List<Reference<?>>> wildcardEvent = registry.get("*");
        if (wildcardEvent != null) {
            List<Reference<?>> exact = wildcardEvent.get(resourceKey);
            if (exact != null) result.addAll(exact);
            List<Reference<?>> wc = wildcardEvent.get("*");
            if (wc != null) result.addAll(wc);
        }
        return result;
    }

    public static void unregister(String eventType, String resourceKey, Reference<?> ref) {
        Map<String, List<Reference<?>>> eventMap = registry.get(eventType);
        if (eventMap != null) {
            List<Reference<?>> refs = eventMap.get(resourceKey);
            if (refs != null) refs.remove(ref);
        }
    }

    public static void clear() {
        registry.clear();
    }

    private TriggerRegistry() {}
}
