package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure helpers for resolving room indexes against immutable topology. */
public final class MapNavigation {

    private MapNavigation() {}

    /**
     * Resolves a 0-based outgoing edge index from {@code fromNodeId}.
     * Returns null when the map, origin, or index is invalid.
     */
    public static String resolveOutgoing(MapDefinition map, String fromNodeId, int outgoingIndex) {
        if (map == null || fromNodeId == null || fromNodeId.isEmpty() || outgoingIndex < 0) return null;
        MapNode from = map.getNode(fromNodeId);
        if (from == null) return null;
        if (!from.outgoingNodeIds.isEmpty()) {
            if (outgoingIndex >= from.outgoingNodeIds.size()) return null;
            return from.outgoingNodeIds.get(outgoingIndex);
        }
        // Captured mid-path nodes may lack edges; fall back to next-row candidates.
        List<String> fallback = forwardFallback(map, from);
        if (outgoingIndex >= fallback.size()) return null;
        return fallback.get(outgoingIndex);
    }

    /** Next-row (y+1) nodes sorted; used when a node has empty outgoing list. */
    public static List<String> forwardFallback(MapDefinition map, MapNode from) {
        if (map == null || from == null) return Collections.emptyList();
        List<String> next = new ArrayList<String>();
        for (MapNode node : map.nodes()) {
            if (node.nodeId.equals(from.nodeId)) continue;
            if ("start".equals(node.roomType)) continue;
            if (node.y == from.y + 1) next.add(node.nodeId);
        }
        Collections.sort(next);
        return next;
    }
}
