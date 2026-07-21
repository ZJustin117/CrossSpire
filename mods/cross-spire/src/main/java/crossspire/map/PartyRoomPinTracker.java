package crossspire.map;

import crossspire.party.PartyState;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Party-scoped map-pin aggregation; unanimous node_id among online members. */
public final class PartyRoomPinTracker {

    private final Map<String, Map<String, String>> pinsByParty = new HashMap<String, Map<String, String>>();

    public synchronized boolean pin(PartyState party, String playerId, String nodeId) {
        if (party == null || playerId == null || nodeId == null || nodeId.isEmpty()
            || !party.memberIds.contains(playerId)) {
            return false;
        }
        Map<String, String> pins = pinsByParty.get(party.partyId);
        if (pins == null) {
            pins = new LinkedHashMap<String, String>();
            pinsByParty.put(party.partyId, pins);
        }
        pins.put(playerId, nodeId);
        return true;
    }

    public synchronized String consensusNodeId(PartyState party) {
        if (party == null) return null;
        Map<String, String> pins = pinsByParty.get(party.partyId);
        if (pins == null || pins.size() != party.memberIds.size()) return null;
        String nodeId = null;
        for (String memberId : party.memberIds) {
            String pin = pins.get(memberId);
            if (pin == null || (nodeId != null && !nodeId.equals(pin))) return null;
            nodeId = pin;
        }
        return nodeId;
    }

    public synchronized Map<String, String> pins(String partyId) {
        Map<String, String> pins = pinsByParty.get(partyId);
        if (pins == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(pins));
    }

    public synchronized void clear(String partyId) {
        pinsByParty.remove(partyId);
    }
}
