package crossspire.event;

import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventApprovalCoordinatorTest {

    @Test
    public void individualModeApprovesValidMemberChoiceOnce() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        PartyState party = parties.getParty("P0");
        EventApprovalCoordinator coordinator = new EventApprovalCoordinator();
        assertTrue(coordinator.registerInterface(iface("E1", "hash-a", 0, 1), party));

        Protocol.EventChoiceRequestPayload req = request("E1", "req-1", 0, "hash-a");
        EventApprovalCoordinator.Decision first = coordinator.decide(party, "alice", req);
        assertTrue(first.approved);
        assertEquals("req-1", first.payload.requestId);

        EventApprovalCoordinator.Decision dup = coordinator.decide(party, "alice", req);
        assertFalse(dup.approved);
        assertEquals("duplicate_request", dup.reason);

        Protocol.EventPlayerResultPayload result = new Protocol.EventPlayerResultPayload();
        result.eventInstanceId = "E1";
        result.partyId = "P0";
        result.requestId = "req-1";
        result.playerId = "alice";
        assertTrue(coordinator.acceptPlayerResult(party, "alice", result));
        assertFalse(coordinator.acceptPlayerResult(party, "alice", result));
    }

    @Test
    public void rejectsUnknownEventDisabledOptionAndOutsider() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        PartyState party = parties.getParty("P0");
        EventApprovalCoordinator coordinator = new EventApprovalCoordinator();
        assertTrue(coordinator.registerInterface(iface("E1", "hash-a", 0), party));

        assertFalse(coordinator.decide(party, "alice", request("missing", "r1", 0, "hash-a")).approved);
        assertEquals("option_disabled",
            coordinator.decide(party, "alice", request("E1", "r2", 9, "hash-a")).reason);
        assertEquals("not_member",
            coordinator.decide(party, "charlie", request("E1", "r3", 0, "hash-a")).reason);
        assertEquals("hash_mismatch",
            coordinator.decide(party, "alice", request("E1", "r4", 0, "other")).reason);
    }

    @Test
    public void votingModeIsRejectedUntilT75() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "");
        PartyState party = parties.getParty("P0");
        EventApprovalCoordinator coordinator = new EventApprovalCoordinator();
        Protocol.EventInterfacePayload iface = iface("E1", "h", 0);
        iface.mode = EventApprovalCoordinator.MODE_VOTING;
        assertTrue(coordinator.registerInterface(iface, party));
        assertEquals("voting_deferred",
            coordinator.decide(party, "alice", request("E1", "r1", 0, "h")).reason);
    }

    private static Protocol.EventInterfacePayload iface(String id, String hash, int... enabled) {
        Protocol.EventInterfacePayload payload = new Protocol.EventInterfacePayload();
        payload.eventInstanceId = id;
        payload.partyId = "P0";
        payload.eventId = "BigFish";
        payload.eventClass = "com.megacrit.cardcrawl.events.shrines.BigFish";
        payload.resourceHash = hash;
        payload.mode = EventApprovalCoordinator.MODE_INDIVIDUAL;
        payload.options = new Protocol.EventOptionInfo[enabled.length];
        for (int i = 0; i < enabled.length; i++) {
            Protocol.EventOptionInfo opt = new Protocol.EventOptionInfo();
            opt.index = enabled[i];
            opt.text = "opt" + enabled[i];
            opt.enabled = true;
            payload.options[i] = opt;
        }
        return payload;
    }

    private static Protocol.EventChoiceRequestPayload request(String eventId, String requestId,
                                                              int option, String hash) {
        Protocol.EventChoiceRequestPayload req = new Protocol.EventChoiceRequestPayload();
        req.eventInstanceId = eventId;
        req.partyId = "P0";
        req.requestId = requestId;
        req.uiStep = "buttonEffect";
        req.optionIndex = option;
        req.resourceHash = hash;
        return req;
    }
}
