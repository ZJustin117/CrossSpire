package crossspire.map;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MapDirectoryTest {

    @Test
    public void mapRegistrationIsImmutableAndIdempotent() {
        MapRegistry registry = new MapRegistry();
        MapDefinition map = map("M1", "a", "a", "b");

        assertSame(map, registry.register("alice", map));
        assertSame(map, registry.register("alice", map));
        assertNull(registry.register("bob", map("M1", "a", "a", "c")));
        assertEquals("alice", registry.getMapHostId("M1"));
        assertSame(map, registry.get("M1"));
    }

    @Test
    public void allocationIsIdempotentPerPartyNodeAndVisitButSeparatesParties() {
        MapDefinition map = map("M1", "a", "a", "b");
        NodeInstanceRegistry registry = new NodeInstanceRegistry();

        NodeInstance first = registry.allocate(map, "P0", "a", "b", 1, "alice");
        assertNotNull(first);
        assertSame(first, registry.allocate(map, "P0", "a", "b", 1, "alice"));

        NodeInstance otherParty = registry.allocate(map, "P1", "a", "b", 1, "bob");
        assertNotNull(otherParty);
        assertEquals(false, first.nodeInstanceId.equals(otherParty.nodeInstanceId));
    }

    @Test
    public void allocationRejectsUnknownOrUnreachableNodes() {
        MapDefinition map = map("M1", "a", "a", "b");
        NodeInstanceRegistry registry = new NodeInstanceRegistry();

        assertNull(registry.allocate(map, "P0", "a", "missing", 1, "alice"));
        assertNull(registry.allocate(map, "P0", "b", "a", 1, "alice"));
    }

    private static MapDefinition map(String mapId, String start, String from, String to) {
        return new MapDefinition(mapId, "EXORDIUM", 1, "digest", start, Arrays.asList(
            new MapNode(from, Arrays.asList(to)),
            new MapNode(to, Arrays.<String>asList())));
    }
}
