package crossspire.network;

import java.util.List;
import java.util.Arrays;
import crossspire.map.MapDefinition;
import crossspire.map.MapNode;
import crossspire.party.PartyManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoomHostTest {

    @Test
    public void newRoomHostHasNoPlayers() {
        RoomHost host = new RoomHost("host-123");
        assertEquals(0, host.getPlayerCount());
    }

    @Test
    public void playerIdIsAsAssigned() {
        RoomHost host = new RoomHost("host-123");
        assertEquals("host-123", host.getHostPlayerId());
    }

    @Test
    public void roomHostOwnsMapAndNodeInstanceDirectories() {
        RoomHost host = new RoomHost("host-123");
        MapDefinition map = new MapDefinition("M1", "EXORDIUM", 1, "digest", "a",
            Arrays.asList(new MapNode("a", Arrays.asList("b")),
                new MapNode("b", Arrays.<String>asList())));

        assertSame(map, host.getMapRegistry().register("alice", map));
        assertNotNull(host.getNodeInstanceRegistry().allocate(map, "P0", "a", "b", 1, "alice"));
    }

    @Test
    public void roomHostOwnsPartyScopedHostElectionTracker() {
        RoomHost host = new RoomHost("host-123");
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");

        assertTrue(host.getPartyHostElectionTracker().castMapHostVote(
            parties.getParty("P0"), "alice", "bob"));
        assertTrue(host.getPartyHostElectionTracker().castMapHostVote(
            parties.getParty("P0"), "bob", "bob"));
        assertEquals("bob", host.getPartyHostElectionTracker().mapHostConsensus(parties.getParty("P0")));
    }

    @Test
    public void addPlayerIncreasesCount() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("player-2");
        assertEquals(1, host.getPlayerCount());
        assertTrue(host.hasPlayer("player-2"));
    }

    @Test
    public void removePlayerDecreasesCount() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("player-2");
        host.removePlayer("player-2");
        assertEquals(0, host.getPlayerCount());
        assertFalse(host.hasPlayer("player-2"));
    }

    @Test
    public void getOtherPlayersExcludesSelf() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("host-123");
        host.addPlayer("player-2");
        host.addPlayer("player-3");
        List<String> others = host.getOtherPlayers("host-123");
        assertEquals(2, others.size());
        assertTrue(others.contains("player-2"));
        assertTrue(others.contains("player-3"));
        assertFalse(others.contains("host-123"));
    }

    @Test
    public void shouldReachConsensusWhenAllPinsMatch() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.addPlayer("bob");
        host.pinRoom("host", 1);
        host.pinRoom("alice", 1);
        host.pinRoom("bob", 1);
        assertEquals(1, host.checkConsensus());
    }

    @Test
    public void shouldReturnNegativeWhenNoConsensus() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.pinRoom("host", 1);
        host.pinRoom("alice", 2);
        assertEquals(-1, host.checkConsensus());
    }

    @Test
    public void shouldReturnNegativeWhenNotAllPinned() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.pinRoom("host", 1);
        assertEquals(-1, host.checkConsensus());
    }

    @Test
    public void shouldElectFirstAlphabetically() {
        RoomHost host = new RoomHost("zebra");
        host.addPlayer("zebra");
        host.addPlayer("alpha");
        host.addPlayer("gamma");
        assertEquals("alpha", host.electNewHost());
    }

    @Test
    public void shouldElectSelfWhenOnlyRemainingPlayer() {
        RoomHost host = new RoomHost("alpha");
        host.addPlayer("alpha");
        assertEquals("alpha", host.electNewHost());
    }

    @Test
    public void shouldReturnNullWhenNoPlayers() {
        RoomHost host = new RoomHost("host");
        assertNull(host.electNewHost());
    }

    @Test
    public void playerLeftShouldRemovePins() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.addPlayer("bob");
        host.pinRoom("host", 1);
        host.pinRoom("alice", 1);
        host.pinRoom("bob", 1);
        assertEquals(1, host.checkConsensus());
        host.removePlayer("bob");
        assertEquals(1, host.checkConsensus());
    }

    @Test
    public void shouldReachStageVoteConsensusWhenAllVoteSame() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.addPlayer("bob");
        host.castVote("host", "bob");
        host.castVote("alice", "bob");
        host.castVote("bob", "bob");
        assertEquals("bob", host.checkStageVoteConsensus());
    }

    @Test
    public void shouldReturnNullWhenStageVoteNotAllVoted() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.castVote("host", "bob");
        assertNull(host.checkStageVoteConsensus());
    }

    @Test
    public void shouldReturnNullWhenStageVotesDiverge() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.castVote("host", "bob");
        host.castVote("alice", "alice");
        assertNull(host.checkStageVoteConsensus());
    }

    @Test
    public void stageVoteShouldBeUpdatedOnRecast() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.castVote("host", "bob");
        host.castVote("alice", "bob");
        assertEquals("bob", host.checkStageVoteConsensus());
        host.castVote("alice", "alice");
        assertNull(host.checkStageVoteConsensus());
    }

    @Test
    public void endTurnConsensusRequiresAllPlayers() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.markEndTurn("host");
        assertFalse(host.checkEndTurnConsensus());
        assertEquals(1, host.getEndTurnReadyCount());
        host.markEndTurn("alice");
        assertTrue(host.checkEndTurnConsensus());
        assertEquals(2, host.getEndTurnReadyCount());
        host.clearEndTurns();
        assertFalse(host.checkEndTurnConsensus());
        assertEquals(0, host.getEndTurnReadyCount());
    }

    @Test
    public void removePlayerClearsEndTurn() {
        RoomHost host = new RoomHost("host");
        host.addPlayer("host");
        host.addPlayer("alice");
        host.markEndTurn("alice");
        host.removePlayer("alice");
        host.markEndTurn("host");
        assertTrue(host.checkEndTurnConsensus());
    }
}
