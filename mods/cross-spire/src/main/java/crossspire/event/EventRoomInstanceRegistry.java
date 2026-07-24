package crossspire.event;

import crossspire.network.Protocol;
import crossspire.party.PartyState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RoomHost/NIH-side directory of event-room instances.
 * Keyed by (party_id, event_instance_id, option_index); same option reuses one path.
 */
public final class EventRoomInstanceRegistry {

    private final Map<String, EventRoomInstance> byKey = new LinkedHashMap<String, EventRoomInstance>();
    private final Map<String, EventRoomInstance> byInstanceId = new LinkedHashMap<String, EventRoomInstance>();

    public synchronized EventRoomInstance allocateOrJoin(PartyState party, String eventInstanceId,
                                                         int optionIndex, String chooserId) {
        if (party == null || empty(eventInstanceId) || empty(chooserId)
            || !party.memberIds.contains(chooserId)) {
            return null;
        }
        String key = key(party.partyId, eventInstanceId, optionIndex);
        EventRoomInstance existing = byKey.get(key);
        if (existing != null) {
            existing.addMember(chooserId);
            return existing;
        }
        String instanceId = "er:" + party.partyId + ":" + eventInstanceId + ":" + optionIndex;
        EventRoomInstance created = new EventRoomInstance(
            instanceId, party.partyId, eventInstanceId, optionIndex,
            party.nodeInstanceHostId, chooserId);
        byKey.put(key, created);
        byInstanceId.put(instanceId, created);
        return created;
    }

    public synchronized EventRoomInstance get(String instanceId) {
        return byInstanceId.get(instanceId);
    }

    public synchronized List<EventRoomInstance> snapshot() {
        return Collections.unmodifiableList(new ArrayList<EventRoomInstance>(byInstanceId.values()));
    }

    public static Protocol.SharedOutcome toSharedOutcome(EventRoomInstance instance) {
        return toSharedOutcome(instance, "Cultist");
    }

    public static Protocol.SharedOutcome toSharedOutcome(EventRoomInstance instance, String encounter) {
        if (instance == null) return null;
        Protocol.SharedOutcome outcome = new Protocol.SharedOutcome();
        outcome.type = "event_room";
        outcome.instanceId = instance.instanceId;
        outcome.optionIndex = instance.optionIndex;
        outcome.encounter = encounter != null && !encounter.isEmpty() ? encounter : "Cultist";
        java.util.List<String> members = instance.members();
        outcome.memberIds = members.toArray(new String[0]);
        return outcome;
    }

    public static Protocol.SharedOutcome leavePartyOutcome(int optionIndex) {
        Protocol.SharedOutcome outcome = new Protocol.SharedOutcome();
        outcome.type = "leave_party";
        outcome.optionIndex = optionIndex;
        return outcome;
    }

    static String key(String partyId, String eventInstanceId, int optionIndex) {
        return partyId + "|" + eventInstanceId + "|" + optionIndex;
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
