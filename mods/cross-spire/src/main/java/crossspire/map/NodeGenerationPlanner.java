package crossspire.map;

import crossspire.event.EventInterfaceFactory;
import crossspire.network.Protocol;

/** Pure, deterministic first-pass node content planner. */
public final class NodeGenerationPlanner {

    private NodeGenerationPlanner() {}

    public static Protocol.NodeGenerationResult plan(MapNode node, Protocol.NodeInstanceInfo instance) {
        if (node == null || instance == null || node.nodeId == null
            || !node.nodeId.equals(instance.nodeId)) {
            return null;
        }
        Protocol.NodeGenerationResult result = new Protocol.NodeGenerationResult();
        result.nodeId = node.nodeId;
        result.roomType = node.roomType;
        if ("monster".equals(node.roomType)) {
            result.encounter = "Cultist";
            return result;
        }
        if ("event".equals(node.roomType)) {
            result.eventInterface = EventInterfaceFactory.create(instance);
            return result.eventInterface != null ? result : null;
        }
        return null;
    }
}
