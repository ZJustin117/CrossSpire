package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable map-topology node; it deliberately has no generated room content. */
public final class MapNode {

    public final String nodeId;
    public final String roomType;
    public final List<String> outgoingNodeIds;

    public MapNode(String nodeId, List<String> outgoingNodeIds) {
        this(nodeId, "monster", outgoingNodeIds);
    }

    public MapNode(String nodeId, String roomType, List<String> outgoingNodeIds) {
        this.nodeId = nodeId;
        this.roomType = roomType != null && !roomType.isEmpty() ? roomType : "monster";
        this.outgoingNodeIds = Collections.unmodifiableList(new ArrayList<String>(outgoingNodeIds));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MapNode)) return false;
        MapNode that = (MapNode) other;
        return nodeId.equals(that.nodeId) && roomType.equals(that.roomType)
            && outgoingNodeIds.equals(that.outgoingNodeIds);
    }

    @Override
    public int hashCode() {
        int hash = 31 * nodeId.hashCode() + roomType.hashCode();
        return 31 * hash + outgoingNodeIds.hashCode();
    }
}
