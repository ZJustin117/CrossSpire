package crossspire.map;

import crossspire.network.Protocol;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void plansEliteBossAndNonCombatRoomTypes() {
        Protocol.NodeInstanceInfo eliteInfo = instance("elite-node");
        Protocol.NodeInstanceInfo bossInfo = instance("boss-node");
        Protocol.NodeInstanceInfo shopInfo = instance("shop-node");
        Protocol.NodeInstanceInfo restInfo = instance("rest-node");
        Protocol.NodeInstanceInfo treasureInfo = instance("treasure-node");

        Protocol.NodeGenerationResult elite = NodeGenerationPlanner.plan(
            new MapNode("elite-node", "elite", Collections.<String>emptyList()), eliteInfo);
        Protocol.NodeGenerationResult boss = NodeGenerationPlanner.plan(
            new MapNode("boss-node", "boss", Collections.<String>emptyList()), bossInfo);
        Protocol.NodeGenerationResult shop = NodeGenerationPlanner.plan(
            new MapNode("shop-node", "shop", Collections.<String>emptyList()), shopInfo);
        Protocol.NodeGenerationResult rest = NodeGenerationPlanner.plan(
            new MapNode("rest-node", "rest", Collections.<String>emptyList()), restInfo);
        Protocol.NodeGenerationResult treasure = NodeGenerationPlanner.plan(
            new MapNode("treasure-node", "treasure", Collections.<String>emptyList()), treasureInfo);

        assertEquals("elite", elite.roomType);
        assertNotNull(elite.encounter);
        assertTrue(elite.encounter.equals("Gremlin Nob")
            || elite.encounter.equals("Lagavulin")
            || elite.encounter.equals("3 Sentries"));
        assertEquals("boss", boss.roomType);
        assertEquals("The Guardian", boss.encounter);
        assertEquals("shop", shop.roomType);
        assertNull(shop.encounter);
        assertEquals("rest", rest.roomType);
        assertEquals("treasure", treasure.roomType);
        assertNotNull(shop.shopSeed);
        assertTrue(shop.shopSeed.startsWith("shop:"));
        assertNotNull(rest.restOptions);
        assertTrue(rest.restOptions.length >= 3);
        assertTrue("small_chest".equals(treasure.treasureTier)
            || "medium_chest".equals(treasure.treasureTier)
            || "large_chest".equals(treasure.treasureTier));
    }

    @Test
    public void shopAndTreasureSeedsAreDeterministicPerInstance() {
        Protocol.NodeInstanceInfo a = instance("shop-a");
        Protocol.NodeInstanceInfo b = instance("shop-a");
        Protocol.NodeGenerationResult first = NodeGenerationPlanner.plan(
            new MapNode("shop-a", "shop", Collections.<String>emptyList()), a);
        Protocol.NodeGenerationResult second = NodeGenerationPlanner.plan(
            new MapNode("shop-a", "shop", Collections.<String>emptyList()), b);
        assertEquals(first.shopSeed, second.shopSeed);

        Protocol.NodeInstanceInfo t = instance("t-node");
        Protocol.NodeGenerationResult treasure = NodeGenerationPlanner.plan(
            new MapNode("t-node", "treasure", Collections.<String>emptyList()), t);
        Protocol.NodeGenerationResult treasure2 = NodeGenerationPlanner.plan(
            new MapNode("t-node", "treasure", Collections.<String>emptyList()), t);
        assertEquals(treasure.treasureTier, treasure2.treasureTier);
    }

    @Test
    public void resolvesUnknownQuestionMarkNodesDeterministically() {
        Protocol.NodeInstanceInfo instance = instance("q-node");

        Protocol.NodeGenerationResult resolved = NodeGenerationPlanner.plan(
            new MapNode("q-node", "unknown", Collections.<String>emptyList()), instance);

        assertNotNull(resolved);
        assertTrue("event".equals(resolved.roomType) || "monster".equals(resolved.roomType));
        if ("monster".equals(resolved.roomType)) {
            assertNotNull(resolved.encounter);
        } else {
            assertNotNull(resolved.eventInterface);
        }
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
