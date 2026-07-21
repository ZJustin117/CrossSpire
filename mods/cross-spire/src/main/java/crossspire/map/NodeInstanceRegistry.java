package crossspire.map;

import java.util.LinkedHashMap;
import java.util.Map;

/** RoomHost directory for idempotent, party-isolated node instance allocation. */
public final class NodeInstanceRegistry {

    private final Map<String, NodeInstance> instancesByKey = new LinkedHashMap<String, NodeInstance>();

    public synchronized NodeInstance allocate(MapDefinition map, String partyId, String fromNodeId,
                                              String nodeId, int visitId, String nodeInstanceHostId) {
        if (map == null || empty(partyId) || empty(nodeId) || empty(nodeInstanceHostId) || visitId < 1) return null;
        if (map.getNode(nodeId) == null || !isReachable(map, fromNodeId, nodeId)) return null;
        String key = key(map.mapInstanceId, partyId, nodeId, visitId);
        NodeInstance existing = instancesByKey.get(key);
        if (existing != null) return existing;
        NodeInstance allocated = new NodeInstance("node:" + key, map.mapInstanceId, partyId,
            nodeId, visitId, nodeInstanceHostId);
        instancesByKey.put(key, allocated);
        return allocated;
    }

    public synchronized NodeInstance get(String mapInstanceId, String partyId, String nodeId, int visitId) {
        return instancesByKey.get(key(mapInstanceId, partyId, nodeId, visitId));
    }

    private static boolean isReachable(MapDefinition map, String fromNodeId, String nodeId) {
        return fromNodeId == null || fromNodeId.isEmpty()
            ? map.startNodeId.equals(nodeId) : map.hasEdge(fromNodeId, nodeId);
    }

    private static String key(String mapInstanceId, String partyId, String nodeId, int visitId) {
        return mapInstanceId + "/" + partyId + "/" + nodeId + "/" + visitId;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
