package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable registered map topology owned by RoomHost's directory. */
public final class MapDefinition {

    public final String mapInstanceId;
    public final String actId;
    public final int mapRevision;
    public final String generationDigest;
    public final String startNodeId;
    private final Map<String, MapNode> nodesById;

    public MapDefinition(String mapInstanceId, String actId, int mapRevision,
                         String generationDigest, String startNodeId, List<MapNode> nodes) {
        this.mapInstanceId = mapInstanceId;
        this.actId = actId;
        this.mapRevision = mapRevision;
        this.generationDigest = generationDigest;
        this.startNodeId = startNodeId;
        Map<String, MapNode> indexed = new LinkedHashMap<String, MapNode>();
        for (MapNode node : nodes) {
            if (node == null || empty(node.nodeId) || indexed.put(node.nodeId, node) != null) {
                throw new IllegalArgumentException("Map nodes require unique IDs");
            }
        }
        if (empty(mapInstanceId) || empty(actId) || empty(startNodeId) || !indexed.containsKey(startNodeId)) {
            throw new IllegalArgumentException("Map definition requires ID, act, and known start node");
        }
        for (MapNode node : indexed.values()) {
            for (String outgoing : node.outgoingNodeIds) {
                if (!indexed.containsKey(outgoing)) {
                    throw new IllegalArgumentException("Map edge points to unknown node");
                }
            }
        }
        this.nodesById = Collections.unmodifiableMap(indexed);
    }

    public MapNode getNode(String nodeId) {
        return nodesById.get(nodeId);
    }

    public List<MapNode> nodes() {
        return Collections.unmodifiableList(new ArrayList<MapNode>(nodesById.values()));
    }

    public boolean hasEdge(String fromNodeId, String toNodeId) {
        MapNode from = getNode(fromNodeId);
        return from != null && from.outgoingNodeIds.contains(toNodeId);
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
