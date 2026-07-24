package crossspire.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable map-topology node; it deliberately has no generated room content. */
public final class MapNode {

    public final String nodeId;
    public final int x;
    public final int y;
    public final String roomType;
    public final String icon;
    public final boolean burningElite;
    public final List<String> outgoingNodeIds;

    public MapNode(String nodeId, List<String> outgoingNodeIds) {
        this(nodeId, "monster", outgoingNodeIds);
    }

    public MapNode(String nodeId, String roomType, List<String> outgoingNodeIds) {
        this(nodeId, 0, 0, roomType, "", false, outgoingNodeIds);
    }

    public MapNode(String nodeId, int x, int y, String roomType, String icon, boolean burningElite,
                   List<String> outgoingNodeIds) {
        this.nodeId = nodeId;
        this.x = x;
        this.y = y;
        this.roomType = roomType != null && !roomType.isEmpty() ? roomType : "monster";
        this.icon = icon != null ? icon : "";
        this.burningElite = burningElite;
        this.outgoingNodeIds = Collections.unmodifiableList(new ArrayList<String>(outgoingNodeIds));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MapNode)) return false;
        MapNode that = (MapNode) other;
        return nodeId.equals(that.nodeId) && x == that.x && y == that.y && roomType.equals(that.roomType)
            && icon.equals(that.icon) && burningElite == that.burningElite
            && outgoingNodeIds.equals(that.outgoingNodeIds);
    }

    @Override
    public int hashCode() {
        int hash = 31 * nodeId.hashCode() + x;
        hash = 31 * hash + y;
        hash = 31 * hash + roomType.hashCode();
        hash = 31 * hash + icon.hashCode();
        hash = 31 * hash + (burningElite ? 1 : 0);
        return 31 * hash + outgoingNodeIds.hashCode();
    }
}
