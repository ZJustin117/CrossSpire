package crossspire.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Filters room-level player projections to the local player's gameplay party. */
public final class PartyVisibility {

    private PartyVisibility() {}

    public static List<String> visibleRemotePlayerIds(PartyManager manager, String localPlayerId,
                                                       Collection<String> remotePlayerIds) {
        if (remotePlayerIds == null || remotePlayerIds.isEmpty()) return Collections.emptyList();
        if (manager == null || localPlayerId == null || localPlayerId.isEmpty()) {
            return new ArrayList<String>(remotePlayerIds);
        }
        String partyId = manager.getPartyIdForPlayer(localPlayerId);
        PartyState party = partyId != null ? manager.getParty(partyId) : null;
        if (party == null) return Collections.emptyList();

        List<String> visible = new ArrayList<String>();
        for (String remotePlayerId : remotePlayerIds) {
            if (remotePlayerId != null && party.memberIds.contains(remotePlayerId)
                && !localPlayerId.equals(remotePlayerId)) {
                visible.add(remotePlayerId);
            }
        }
        return visible;
    }
}
