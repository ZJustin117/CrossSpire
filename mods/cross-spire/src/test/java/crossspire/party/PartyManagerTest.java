package crossspire.party;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T7.1 party directory transitions, independent of transport and STS state. */
public class PartyManagerTest {

    @Test
    public void defaultPartyElectsLexicographicallySmallestLeader() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("charlie", "alice", "bob"), "node-4");

        PartyState party = manager.getParty(PartyManager.DEFAULT_PARTY_ID);
        assertNotNull(party);
        assertEquals("alice", party.leaderId);
        assertEquals(Arrays.asList("alice", "bob", "charlie"), party.memberIds);
        assertEquals("node-4", party.mapPosition);
    }

    @Test
    public void leaveCreatesSingleMemberPartyAndPreservesPosition() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "node-4");

        PartyState solo = manager.leave("bob");
        assertNotNull(solo);
        assertEquals("bob", solo.leaderId);
        assertEquals(Arrays.asList("bob"), solo.memberIds);
        assertEquals("node-4", solo.mapPosition);
        assertEquals(Arrays.asList("alice"),
            manager.getParty(PartyManager.DEFAULT_PARTY_ID).memberIds);
    }

    @Test
    public void joinRequiresTargetLeaderApproval() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        PartyState solo = manager.leave("charlie");

        assertTrue(manager.requestJoin("request-1", "charlie", PartyManager.DEFAULT_PARTY_ID));
        assertEquals(solo.partyId, manager.getPartyIdForPlayer("charlie"));
        assertFalse(manager.approveJoinRequest("request-1", "bob"));
        assertTrue(manager.approveJoinRequest("request-1", "alice"));
        assertEquals(PartyManager.DEFAULT_PARTY_ID, manager.getPartyIdForPlayer("charlie"));
        assertNull(manager.getParty(solo.partyId));
    }

    @Test
    public void rejectionLeavesMembershipUnchangedAndRecordsReason() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        PartyState solo = manager.leave("charlie");

        assertTrue(manager.requestJoin("request-2", "charlie", PartyManager.DEFAULT_PARTY_ID));
        assertTrue(manager.rejectJoinRequest("request-2", "alice", "full"));
        assertEquals(solo.partyId, manager.getPartyIdForPlayer("charlie"));
        assertEquals("full", manager.getJoinRequest("request-2").reason);
    }

    @Test
    public void disconnectReelectsLeaderAndRemovesRelatedPendingRequests() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob", "charlie"), "node-4");
        manager.leave("charlie");
        assertTrue(manager.requestJoin("request-3", "charlie", PartyManager.DEFAULT_PARTY_ID));

        manager.removePlayer("alice");
        assertEquals("bob", manager.getParty(PartyManager.DEFAULT_PARTY_ID).leaderId);
        manager.removePlayer("charlie");
        assertNull(manager.getJoinRequest("request-3"));
    }

    @Test
    public void snapshotReplacementIsAtomicAndValidatesLeaderAndMembership() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice"), "old");
        PartyState valid = new PartyState("P0", Arrays.asList("bob", "alice"), "node-4");

        assertTrue(manager.replaceSnapshot(Arrays.asList(valid)));
        assertEquals("alice", manager.getParty("P0").leaderId);
        assertEquals("P0", manager.getPartyIdForPlayer("bob"));

        PartyState duplicate = new PartyState("P1", Arrays.asList("alice", "charlie"), "node-5");
        assertFalse(manager.replaceSnapshot(Arrays.asList(valid, duplicate)));
        assertEquals("P0", manager.getPartyIdForPlayer("bob"));
        assertNull(manager.getPartyIdForPlayer("charlie"));
    }

    @Test
    public void newConnectionJoinsDefaultPartyWithoutResettingExistingSplits() {
        PartyManager manager = new PartyManager();
        manager.initializeDefaultParty(Arrays.asList("alice", "bob"), "node-4");
        PartyState solo = manager.leave("bob");

        assertTrue(manager.addPlayerToDefaultParty("charlie"));
        assertEquals(Arrays.asList("alice", "charlie"),
            manager.getParty(PartyManager.DEFAULT_PARTY_ID).memberIds);
        assertEquals(Arrays.asList("bob"), manager.getParty(solo.partyId).memberIds);
        assertEquals(solo.partyId, manager.getPartyIdForPlayer("bob"));
    }
}
