package crossspire.map;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Converts protocol DTOs into validated immutable map directory values. */
public final class MapProtocolMapper {

    private MapProtocolMapper() {}

    public static MapDefinition fromProtocol(Protocol.MapDefinition payload) {
        if (payload == null || payload.nodes == null) return null;
        List<MapNode> nodes = new ArrayList<MapNode>();
        for (Protocol.MapNode node : payload.nodes) {
            if (node == null || node.nodeId == null || node.outgoingNodeIds == null) return null;
            nodes.add(new MapNode(node.nodeId, Arrays.asList(node.outgoingNodeIds)));
        }
        try {
            return new MapDefinition(payload.mapInstanceId, payload.actId, payload.mapRevision,
                payload.generationDigest != null ? payload.generationDigest : "", payload.startNodeId, nodes);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
