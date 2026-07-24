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
    public void preservesNodeCoordinatesAndDisplayMetadataAcrossProtocolRoundTrip() {
        MapNode source = new MapNode("3:7", 3, 7, "elite", "E", true,
            java.util.Collections.singletonList("4:8"));
        MapDefinition definition = new MapDefinition("M1", "EXORDIUM", 1, "digest", "3:7",
            java.util.Arrays.asList(source, new MapNode("4:8", 4, 8, "monster", "M", false,
                java.util.Collections.<String>emptyList())));

        MapDefinition restored = MapProtocolMapper.fromProtocol(MapRegisterSender.toProtocol(definition));

        assertNotNull(restored);
        MapNode node = restored.getNode("3:7");
        assertEquals(3, node.x);
        assertEquals(7, node.y);
        assertEquals("E", node.icon);
        assertEquals(true, node.burningElite);
    }

    @Test
    public void registeredPacketCarriesAuthoritativeTopologyForClientReconstruction() {
        MapDefinition definition = new MapDefinition("M1", "EXORDIUM", 1, "digest", "0:0",
            java.util.Arrays.asList(new MapNode("0:0", 0, 0, "start", "", false,
                java.util.Collections.singletonList("1:1")), new MapNode("1:1", 1, 1, "monster",
                "M", false, java.util.Collections.<String>emptyList())));

        Protocol.MapRegisteredPayload payload = Protocol.GSON.fromJson(
            MapRegisterSender.buildRegistered("P0", definition).payload, Protocol.MapRegisteredPayload.class);

        assertNotNull(payload.map);
        assertNotNull(MapProtocolMapper.fromProtocol(payload.map));
        assertEquals("1:1", payload.map.nodes[1].nodeId);
    }

    @Test
    public void acceptsSyntheticStartAnchorForAnUnselectedVanillaMap() {
        MapDefinition definition = new MapDefinition("M1", "EXORDIUM", 1, "digest", "virtual:start",
            java.util.Arrays.asList(new MapNode("virtual:start", -2, -2, "start", "", false,
                java.util.Collections.singletonList("0:0")), new MapNode("0:0", 0, 0, "monster",
                "M", false, java.util.Collections.<String>emptyList())));

        assertEquals(true, definition.hasEdge("virtual:start", "0:0"));
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
