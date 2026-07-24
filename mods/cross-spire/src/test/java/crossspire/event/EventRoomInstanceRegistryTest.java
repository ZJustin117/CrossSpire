package crossspire.event;

import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EventRoomInstanceRegistryTest {

    @Test
    public void sameOptionReusesInstanceAndCollectsMembers() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob", "carol"), "");
        parties.bindMap("P0", "M1", "start", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        PartyState party = parties.getParty("P0");
        EventRoomInstanceRegistry registry = new EventRoomInstanceRegistry();

        EventRoomInstance first = registry.allocateOrJoin(party, "E1", 1, "alice");
        EventRoomInstance second = registry.allocateOrJoin(party, "E1", 1, "bob");
        EventRoomInstance otherOption = registry.allocateOrJoin(party, "E1", 0, "carol");

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(2, first.size());
        assertTrue(first.contains("alice"));
        assertTrue(first.contains("bob"));
        assertFalse(first.contains("carol"));
        assertNotNull(otherOption);
        assertFalse(first.instanceId.equals(otherOption.instanceId));
        assertEquals(1, otherOption.size());
    }

    @Test
    public void rejectsOutsidersAndBuildsSharedOutcome() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice"), "");
        PartyState party = parties.getParty("P0");
        EventRoomInstanceRegistry registry = new EventRoomInstanceRegistry();

        assertNull(registry.allocateOrJoin(party, "E1", 0, "bob"));
        EventRoomInstance created = registry.allocateOrJoin(party, "E1", 2, "alice");
        assertNotNull(created);
        Protocol.SharedOutcome outcome = EventRoomInstanceRegistry.toSharedOutcome(created, "Cultist");
        assertEquals("event_room", outcome.type);
        assertEquals(created.instanceId, outcome.instanceId);
        assertEquals(2, outcome.optionIndex);
        assertEquals("Cultist", outcome.encounter);
        assertEquals(1, outcome.memberIds.length);
        assertEquals("alice", outcome.memberIds[0]);
    }

    @Test
    public void plannerDetectsFightAndLeaveHeuristics() {
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventId = "BigFish";
        Protocol.EventOptionInfo eat = new Protocol.EventOptionInfo();
        eat.index = 0;
        eat.text = "Eat";
        Protocol.EventOptionInfo fight = new Protocol.EventOptionInfo();
        fight.index = 1;
        fight.text = "Fight";
        Protocol.EventOptionInfo leave = new Protocol.EventOptionInfo();
        leave.index = 2;
        leave.text = "Leave map";
        iface.options = new Protocol.EventOptionInfo[] {eat, fight, leave};

        assertFalse(EventRoomOutcomePlanner.createsEventRoom(iface, 0));
        assertTrue(EventRoomOutcomePlanner.createsEventRoom(iface, 1));
        assertTrue(EventRoomOutcomePlanner.requiresLeaveParty(iface, 2));
        assertFalse(EventRoomOutcomePlanner.requiresLeaveParty(iface, 1));
        assertEquals("Cultist", EventRoomOutcomePlanner.resolveEncounter(iface, 1));
    }

    @Test
    public void onlyListedMembersEnterEventRoom() {
        Protocol.SharedOutcome outcome = new Protocol.SharedOutcome();
        outcome.type = "event_room";
        outcome.memberIds = new String[] {"alice", "bob"};
        assertTrue(EventRoomOutcomePlanner.shouldLocalPlayerEnterEventRoom(outcome, "alice"));
        assertFalse(EventRoomOutcomePlanner.shouldLocalPlayerEnterEventRoom(outcome, "carol"));
        outcome.memberIds = null;
        assertTrue(EventRoomOutcomePlanner.shouldLocalPlayerEnterEventRoom(outcome, "carol"));
    }
}
