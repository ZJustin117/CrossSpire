package crossspire.map;

import java.util.LinkedHashMap;
import java.util.Map;

/** RoomHost's immutable map directory. A map ID can only be registered once. */
public final class MapRegistry {

    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public synchronized MapDefinition register(String mapHostId, MapDefinition map) {
        if (empty(mapHostId) || map == null) return null;
        Entry existing = entries.get(map.mapInstanceId);
        if (existing != null) {
            return existing.map == map || sameDefinition(existing.map, map) ? existing.map : null;
        }
        entries.put(map.mapInstanceId, new Entry(mapHostId, map));
        return map;
    }

    public synchronized MapDefinition get(String mapInstanceId) {
        Entry entry = entries.get(mapInstanceId);
        return entry != null ? entry.map : null;
    }

    public synchronized String getMapHostId(String mapInstanceId) {
        Entry entry = entries.get(mapInstanceId);
        return entry != null ? entry.mapHostId : "";
    }

    private static boolean sameDefinition(MapDefinition first, MapDefinition second) {
        return first.mapInstanceId.equals(second.mapInstanceId)
            && first.actId.equals(second.actId)
            && first.mapRevision == second.mapRevision
            && first.generationDigest.equals(second.generationDigest)
            && first.startNodeId.equals(second.startNodeId)
            && first.nodes().equals(second.nodes());
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }

    private static final class Entry {
        private final String mapHostId;
        private final MapDefinition map;

        private Entry(String mapHostId, MapDefinition map) {
            this.mapHostId = mapHostId;
            this.map = map;
        }
    }
}
