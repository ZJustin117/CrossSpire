package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable map-topology node; it deliberately has no generated room content. */
public final class MapNode {

    public final String nodeId;
    public final List<String> outgoingNodeIds;

    public MapNode(String nodeId, List<String> outgoingNodeIds) {
        this.nodeId = nodeId;
        this.outgoingNodeIds = Collections.unmodifiableList(new ArrayList<String>(outgoingNodeIds));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MapNode)) return false;
        MapNode that = (MapNode) other;
        return nodeId.equals(that.nodeId) && outgoingNodeIds.equals(that.outgoingNodeIds);
    }

    @Override
    public int hashCode() {
        return 31 * nodeId.hashCode() + outgoingNodeIds.hashCode();
    }
}
