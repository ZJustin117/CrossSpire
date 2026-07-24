package crossspire.party;

import crossspire.network.Protocol;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PartyRunStartPlannerTest {

    @Test
    public void rejectsWhenNotAllReady() {
        Set<String> ready = new HashSet<String>(Arrays.asList("alice"));
        assertEquals("not_all_ready",
            PartyRunStartPlanner.rejectReason(Arrays.asList("alice", "bob"), ready, "alice", false));
        assertFalse(PartyRunStartPlanner.allMembersReady(Arrays.asList("alice", "bob"), ready));
    }

    @Test
    public void acceptsWhenAllReadyAndRequesterIsMember() {
        Set<String> ready = new HashSet<String>(Arrays.asList("alice", "bob"));
        assertNull(PartyRunStartPlanner.rejectReason(
            Arrays.asList("alice", "bob"), ready, "bob", false));
        assertTrue(PartyRunStartPlanner.allMembersReady(Arrays.asList("alice", "bob"), ready));
    }

    @Test
    public void rejectsAlreadyStartedAndNonMember() {
        Set<String> ready = new HashSet<String>(Arrays.asList("alice", "bob"));
        assertEquals("already_started",
            PartyRunStartPlanner.rejectReason(Arrays.asList("alice", "bob"), ready, "alice", true));
        assertEquals("not_member",
            PartyRunStartPlanner.rejectReason(Arrays.asList("alice", "bob"), ready, "charlie", false));
    }

    @Test
    public void buildsSharedSeedAndPerPlayerCharacters() {
        Map<String, String> chars = new HashMap<String, String>();
        chars.put("alice", "IRONCLAD");
        chars.put("bob", "THE_SILENT");
        Protocol.PartyRunStart msg = PartyRunStartPlanner.build(
            "P0", "424242", 1, "alice", "alice", Arrays.asList("alice", "bob"), chars);
        assertEquals("party_run_start", msg.type);
        assertEquals("P0", msg.partyId);
        assertEquals("424242", msg.seed);
        assertEquals(1, msg.act);
        assertEquals("alice", msg.leaderId);
        assertEquals(2, msg.members.length);
        assertEquals("IRONCLAD", PartyRunStartPlanner.characterFor(msg, "alice"));
        assertEquals("THE_SILENT", PartyRunStartPlanner.characterFor(msg, "bob"));
        assertEquals("IRONCLAD", PartyRunStartPlanner.characterFor(msg, "unknown"));
    }

    @Test
    public void serializesRoundTrip() {
        Map<String, String> chars = new HashMap<String, String>();
        chars.put("a", "DEFECT");
        Protocol.PartyRunStart msg = PartyRunStartPlanner.build(
            "P0", "seed1", 1, "a", "a", Arrays.asList("a"), chars);
        msg.seq = 7;
        String json = Protocol.GSON.toJson(msg);
        Protocol.PartyRunStart back = Protocol.GSON.fromJson(json, Protocol.PartyRunStart.class);
        assertNotNull(back);
        assertEquals("party_run_start", back.type);
        assertEquals("seed1", back.seed);
        assertEquals("DEFECT", PartyRunStartPlanner.characterFor(back, "a"));
        assertEquals(7, back.seq);
    }
}
