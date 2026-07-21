package crossspire.map;

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
        if (from == null || outgoingIndex >= from.outgoingNodeIds.size()) return null;
        return from.outgoingNodeIds.get(outgoingIndex);
    }
}
