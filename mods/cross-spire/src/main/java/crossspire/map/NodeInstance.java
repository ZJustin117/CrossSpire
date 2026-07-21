package crossspire.map;

/** Directory record for one party's visit to one immutable map node. */
public final class NodeInstance {

    public final String nodeInstanceId;
    public final String mapInstanceId;
    public final String partyId;
    public final String nodeId;
    public final String roomType;
    public final int visitId;
    public final String nodeInstanceHostId;

    NodeInstance(String nodeInstanceId, String mapInstanceId, String partyId,
                  String nodeId, String roomType, int visitId, String nodeInstanceHostId) {
        this.nodeInstanceId = nodeInstanceId;
        this.mapInstanceId = mapInstanceId;
        this.partyId = partyId;
        this.nodeId = nodeId;
        this.roomType = roomType;
        this.visitId = visitId;
        this.nodeInstanceHostId = nodeInstanceHostId;
    }
}
