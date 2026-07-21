package crossspire.map;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MapProtocolMapperTest {

    @Test
    public void mapsValidatedProtocolTopology() {
        Protocol.MapDefinition payload = mapPayload();

        MapDefinition map = MapProtocolMapper.fromProtocol(payload);

        assertNotNull(map);
        assertEquals("M1", map.mapInstanceId);
        assertEquals("a", map.startNodeId);
        assertEquals(true, map.hasEdge("a", "b"));
        assertEquals("event", map.getNode("b").roomType);
    }

    @Test
    public void rejectsPayloadWithUnknownEdgeOrStartNode() {
        Protocol.MapDefinition payload = mapPayload();
        payload.startNodeId = "missing";
        assertNull(MapProtocolMapper.fromProtocol(payload));
    }

    private static Protocol.MapDefinition mapPayload() {
        Protocol.MapNode a = new Protocol.MapNode();
        a.nodeId = "a";
        a.outgoingNodeIds = new String[] {"b"};
        Protocol.MapNode b = new Protocol.MapNode();
        b.nodeId = "b";
        b.roomType = "event";
        b.outgoingNodeIds = new String[0];
        Protocol.MapDefinition payload = new Protocol.MapDefinition();
        payload.mapInstanceId = "M1";
        payload.actId = "EXORDIUM";
        payload.mapRevision = 1;
        payload.generationDigest = "digest";
        payload.startNodeId = "a";
        payload.nodes = new Protocol.MapNode[] {a, b};
        return payload;
    }
}
