package crossspire.map;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Pure topology rules used by STS capture without constructing a live dungeon. */
public class StsMapDefinitionCaptureLogicTest {

    @Test
    public void syntheticStartConnectsOnlyZeroInDegreePlayableNodes() {
        MapNode a = new MapNode("0:0", 0, 0, "monster", "M", false,
            Collections.singletonList("1:1"));
        MapNode b = new MapNode("1:1", 1, 1, "monster", "M", false,
            Collections.<String>emptyList());
        MapNode orphan = new MapNode("3:0", 3, 0, "event", "?", false,
            Collections.<String>emptyList());
        List<MapNode> playable = Arrays.asList(a, b, orphan);

        Set<String> destinations = new HashSet<String>();
        for (MapNode node : playable) destinations.addAll(node.outgoingNodeIds);
        List<String> entries = new java.util.ArrayList<String>();
        for (MapNode node : playable) {
            if (!destinations.contains(node.nodeId)) entries.add(node.nodeId);
        }
        Collections.sort(entries);

        MapDefinition map = new MapDefinition("M1", "Exordium", 1, "digest", "-1:-1",
            Arrays.asList(new MapNode("-1:-1", -1, -1, "start", "", false, entries), a, b, orphan));

        assertNotNull(map);
        assertTrue(map.hasEdge("-1:-1", "0:0"));
        assertTrue(map.hasEdge("-1:-1", "3:0"));
        assertFalse(map.hasEdge("-1:-1", "1:1"));
        assertEquals("monster", map.getNode("0:0").roomType);
    }

    @Test
    public void dropsUnknownOutgoingEdgesOutsideKnownIds() {
        // Mirrors capture filtering boss edges that leave the map grid.
        Set<String> known = new HashSet<String>(Arrays.asList("0:0", "1:1"));
        List<String> outgoing = new java.util.ArrayList<String>();
        for (String dest : Arrays.asList("1:1", "3:16", "1:1")) {
            if (known.contains(dest) && !outgoing.contains(dest)) outgoing.add(dest);
        }
        assertEquals(Collections.singletonList("1:1"), outgoing);
    }

    @Test
    public void emptyStartOutgoingIsRepairedWithZeroInDegreeEntries() {
        MapNode root = new MapNode("0:-1", 0, -1, "start", "", false,
            Collections.<String>emptyList());
        MapNode a = new MapNode("0:0", 0, 0, "monster", "M", false,
            Collections.singletonList("0:1"));
        MapNode b = new MapNode("0:1", 0, 1, "monster", "M", false,
            Collections.<String>emptyList());
        List<MapNode> nodes = Arrays.asList(root, a, b);
        Set<String> destinations = new HashSet<String>();
        for (MapNode node : nodes) destinations.addAll(node.outgoingNodeIds);
        List<String> entries = new java.util.ArrayList<String>();
        for (MapNode node : nodes) {
            if (!destinations.contains(node.nodeId) && !"start".equals(node.roomType)
                && !node.nodeId.equals("0:-1")) {
                entries.add(node.nodeId);
            }
        }
        Collections.sort(entries);
        MapNode repairedRoot = new MapNode("0:-1", 0, -1, "start", "", false, entries);
        MapDefinition map = new MapDefinition("M1", "Exordium", 1, "d", "0:-1",
            Arrays.asList(repairedRoot, a, b));
        assertEquals("0:0", MapNavigation.resolveOutgoing(map, "0:-1", 0));
    }

    @Test
    public void repairEmptyOutgoingLinksFloorZeroToNextRow() {
        MapNode start = new MapNode("0:-1", 0, -1, "start", "", false,
            Collections.singletonList("0:0"));
        MapNode floor0 = new MapNode("0:0", 0, 0, "monster", "M", false,
            Collections.<String>emptyList());
        MapNode floor1a = new MapNode("0:1", 0, 1, "monster", "M", false,
            Collections.<String>emptyList());
        MapNode floor1b = new MapNode("1:1", 1, 1, "event", "?", false,
            Collections.<String>emptyList());
        List<MapNode> repaired = StsMapDefinitionCapture.repairEmptyOutgoing(
            Arrays.asList(start, floor0, floor1a, floor1b), "0:-1");
        MapDefinition map = new MapDefinition("M1", "Exordium", 1, "d", "0:-1", repaired);
        assertFalse(map.getNode("0:0").outgoingNodeIds.isEmpty());
        assertEquals("0:1", MapNavigation.resolveOutgoing(map, "0:0", 0));
        assertEquals("1:1", MapNavigation.resolveOutgoing(map, "0:0", 1));
        assertTrue(map.hasEdge("0:0", "0:1"));
    }

    @Test
    public void resolveOutgoingFallsBackToNextRowWhenEmpty() {
        MapNode a = new MapNode("0:0", 0, 0, "monster", "M", false,
            Collections.<String>emptyList());
        MapNode b = new MapNode("0:1", 0, 1, "monster", "M", false,
            Collections.<String>emptyList());
        MapDefinition map = new MapDefinition("M1", "Exordium", 1, "d", "0:0",
            Arrays.asList(a, b));
        assertEquals("0:1", MapNavigation.resolveOutgoing(map, "0:0", 0));
        assertTrue(MapNavigation.forwardFallback(map, a).contains("0:1"));
    }
}
