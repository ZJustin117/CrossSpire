package crossspire.map;

import crossspire.party.PartyState;

/** Validates that only a party's unanimously elected MapHost registers its immutable map. */
public final class MapRegistrationCoordinator {

    private final MapRegistry maps;
    private final PartyHostElectionTracker elections;

    public MapRegistrationCoordinator(MapRegistry maps, PartyHostElectionTracker elections) {
        this.maps = maps;
        this.elections = elections;
    }

    public synchronized MapDefinition register(PartyState party, String sourcePlayerId,
                                               String declaredMapHostId, MapDefinition map) {
        if (party == null || map == null || sourcePlayerId == null || declaredMapHostId == null) return null;
        String electedMapHostId = elections.mapHostConsensus(party);
        if (electedMapHostId == null || !electedMapHostId.equals(sourcePlayerId)
            || !electedMapHostId.equals(declaredMapHostId)) {
            return null;
        }
        return maps.register(electedMapHostId, map);
    }
}
