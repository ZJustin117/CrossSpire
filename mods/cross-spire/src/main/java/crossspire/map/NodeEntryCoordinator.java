package crossspire.map;

import crossspire.party.PartyState;

/** RoomHost-side path validation + node instance allocation after party consensus. */
public final class NodeEntryCoordinator {

    private final MapRegistry maps;
    private final NodeInstanceRegistry nodes;

    public NodeEntryCoordinator(MapRegistry maps, NodeInstanceRegistry nodes) {
        this.maps = maps;
        this.nodes = nodes;
    }

    public synchronized NodeInstance allocateOnConsensus(PartyState party, String nodeId, int visitId) {
        if (party == null || nodeId == null || nodeId.isEmpty()
            || party.mapInstanceId == null || party.mapInstanceId.isEmpty()
            || party.nodeInstanceHostId == null || party.nodeInstanceHostId.isEmpty()) {
            return null;
        }
        MapDefinition map = maps.get(party.mapInstanceId);
        if (map == null) return null;
        return nodes.allocate(map, party.partyId, party.mapPosition, nodeId, visitId,
            party.nodeInstanceHostId);
    }
}
