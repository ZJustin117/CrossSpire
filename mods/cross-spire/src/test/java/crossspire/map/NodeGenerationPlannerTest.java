package crossspire.map;

import crossspire.network.Protocol;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NodeGenerationPlannerTest {

    @Test
    public void plansEventDescriptorFromImmutableNodeType() {
        Protocol.NodeInstanceInfo instance = instance("event-node");

        Protocol.NodeGenerationResult result = NodeGenerationPlanner.plan(
            new MapNode("event-node", "event", Collections.<String>emptyList()), instance);

        assertNotNull(result);
        assertEquals("event", result.roomType);
        assertEquals("event-node", result.nodeId);
        assertNull(result.encounter);
        assertNotNull(result.eventInterface);
        assertEquals(instance.nodeInstanceId + ":event", result.eventInterface.eventInstanceId);
        assertEquals("P0", result.eventInterface.partyId);
    }

    @Test
    public void plansMonsterEncounterAndRejectsMismatchedAllocation() {
        Protocol.NodeInstanceInfo instance = instance("monster-node");

        Protocol.NodeGenerationResult monster = NodeGenerationPlanner.plan(
            new MapNode("monster-node", "monster", Collections.<String>emptyList()), instance);
        Protocol.NodeGenerationResult mismatch = NodeGenerationPlanner.plan(
            new MapNode("other", "event", Arrays.asList("monster-node")), instance);

        assertEquals("monster", monster.roomType);
        assertEquals("Cultist", monster.encounter);
        assertNull(monster.eventInterface);
        assertNull(mismatch);
    }

    private static Protocol.NodeInstanceInfo instance(String nodeId) {
        Protocol.NodeInstanceInfo instance = new Protocol.NodeInstanceInfo();
        instance.nodeInstanceId = "node:M1/P0/" + nodeId + "/1";
        instance.mapInstanceId = "M1";
        instance.partyId = "P0";
        instance.nodeId = nodeId;
        instance.visitId = 1;
        instance.nodeInstanceHostId = "alice";
        return instance;
    }
}
