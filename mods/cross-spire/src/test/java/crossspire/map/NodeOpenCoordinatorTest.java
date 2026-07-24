package crossspire.map;

import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeOpenCoordinatorTest {

    @Test
    public void acceptsFirstCommitFromElectedNihOnly() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");

        Protocol.NodeInstanceInfo allocated = info("node:M1/P0/a/1", "alice");
        Protocol.NodeGenerationCommitPayload commit = commit(allocated, "Cultist");
        NodeOpenCoordinator coordinator = new NodeOpenCoordinator();

        assertFalse(coordinator.acceptCommit(party, "bob", allocated, commit));
        assertTrue(coordinator.acceptCommit(party, "alice", allocated, commit));
        assertFalse(coordinator.acceptCommit(party, "alice", allocated, commit));
        assertTrue(coordinator.isOpened(allocated.nodeInstanceId));
    }

    @Test
    public void acceptsEventCommitOnlyWhenItContainsAnInterface() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");
        Protocol.NodeInstanceInfo allocated = info("node:M1/P0/event/1", "alice");
        allocated.roomType = "event";

        Protocol.NodeGenerationCommitPayload invalid = commit(allocated, "");
        invalid.generationResult.roomType = "event";
        assertFalse(new NodeOpenCoordinator().acceptCommit(party, "alice", allocated, invalid));

        Protocol.NodeGenerationCommitPayload event = commit(allocated, "");
        event.generationResult = NodeGenerationPlanner.plan(
            new MapNode("a", "event", Arrays.<String>asList()), allocated);
        assertTrue(new NodeOpenCoordinator().acceptCommit(party, "alice", allocated, event));
    }

    @Test
    public void acceptsShopEliteAndResolvedUnknownCommits() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");
        NodeOpenCoordinator coordinator = new NodeOpenCoordinator();

        Protocol.NodeInstanceInfo shop = info("node:M1/P0/shop/1", "alice");
        shop.nodeId = "shop";
        shop.roomType = "shop";
        Protocol.NodeGenerationCommitPayload shopCommit = commit(shop, "");
        shopCommit.generationResult = NodeGenerationPlanner.plan(
            new MapNode("shop", "shop", Arrays.<String>asList()), shop);
        assertTrue(coordinator.acceptCommit(party, "alice", shop, shopCommit));

        Protocol.NodeInstanceInfo elite = info("node:M1/P0/elite/1", "alice");
        elite.nodeId = "elite";
        elite.roomType = "elite";
        Protocol.NodeGenerationCommitPayload eliteCommit = commit(elite, "Gremlin Nob");
        eliteCommit.generationResult = NodeGenerationPlanner.plan(
            new MapNode("elite", "elite", Arrays.<String>asList()), elite);
        assertTrue(coordinator.acceptCommit(party, "alice", elite, eliteCommit));

        Protocol.NodeInstanceInfo unknown = info("node:M1/P0/q/1", "alice");
        unknown.nodeId = "q-node";
        unknown.roomType = "unknown";
        Protocol.NodeGenerationCommitPayload unknownCommit = commit(unknown, "");
        unknownCommit.generationResult = NodeGenerationPlanner.plan(
            new MapNode("q-node", "unknown", Arrays.<String>asList()), unknown);
        assertTrue(new NodeOpenCoordinator().acceptCommit(party, "alice", unknown, unknownCommit));
    }

    private static Protocol.NodeInstanceInfo info(String id, String nih) {
        Protocol.NodeInstanceInfo info = new Protocol.NodeInstanceInfo();
        info.nodeInstanceId = id;
        info.mapInstanceId = "M1";
        info.partyId = "P0";
        info.nodeId = "a";
        info.roomType = "monster";
        info.visitId = 1;
        info.nodeInstanceHostId = nih;
        info.status = "allocated";
        return info;
    }

    private static Protocol.NodeGenerationCommitPayload commit(Protocol.NodeInstanceInfo info, String encounter) {
        Protocol.NodeGenerationCommitPayload commit = new Protocol.NodeGenerationCommitPayload();
        commit.nodeInstanceId = info.nodeInstanceId;
        commit.partyId = info.partyId;
        commit.mapInstanceId = info.mapInstanceId;
        commit.nodeId = info.nodeId;
        commit.generationRevision = 1;
        commit.generationResult = NodeInstanceOpenedSender.defaultMonsterResult(info.nodeId);
        commit.generationResult.encounter = encounter;
        return commit;
    }
}
