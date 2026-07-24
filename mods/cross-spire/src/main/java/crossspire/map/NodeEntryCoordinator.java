package crossspire.map;

import crossspire.party.PartyState;

/** RoomHost-side path validation + node instance allocation after party consensus. */
public final class NodeEntryCoordinator {

    private final MapRegistry maps;
    private final NodeInstanceRegistry nodes;
    private final CrossPartyNodeEncounterRecorder crossPartyEncounters;

    public NodeEntryCoordinator(MapRegistry maps, NodeInstanceRegistry nodes) {
        this(maps, nodes, new CrossPartyNodeEncounterRecorder());
    }

    public NodeEntryCoordinator(MapRegistry maps, NodeInstanceRegistry nodes,
                                CrossPartyNodeEncounterRecorder crossPartyEncounters) {
        this.maps = maps;
        this.nodes = nodes;
        this.crossPartyEncounters = crossPartyEncounters != null
            ? crossPartyEncounters : new CrossPartyNodeEncounterRecorder();
    }

    public synchronized NodeInstance allocateOnConsensus(PartyState party, String nodeId, int visitId) {
        if (party == null || nodeId == null || nodeId.isEmpty()
            || party.mapInstanceId == null || party.mapInstanceId.isEmpty()
            || party.nodeInstanceHostId == null || party.nodeInstanceHostId.isEmpty()) {
            return null;
        }
        MapDefinition map = maps.get(party.mapInstanceId);
        if (map == null) return null;
        NodeInstance existing = nodes.get(party.mapInstanceId, party.partyId, nodeId, visitId);
        NodeInstance allocated = nodes.allocate(map, party.partyId, party.mapPosition, nodeId, visitId,
            party.nodeInstanceHostId);
        // Record only on first allocation for this party/node/visit (not idempotent re-fetch).
        if (allocated != null && existing == null) {
            crossPartyEncounters.noteAllocation(allocated);
        }
        return allocated;
    }

    public CrossPartyNodeEncounterRecorder getCrossPartyEncounters() {
        return crossPartyEncounters;
    }
}
