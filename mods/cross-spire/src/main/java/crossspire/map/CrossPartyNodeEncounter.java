package crossspire.map;

/** Record-only observation that two parties occupy the same map node (no combat merge). */
public final class CrossPartyNodeEncounter {

    public final String mapInstanceId;
    public final String nodeId;
    public final String partyA;
    public final String partyB;
    public final String nodeInstanceA;
    public final String nodeInstanceB;

    public CrossPartyNodeEncounter(String mapInstanceId, String nodeId,
                                   String partyA, String partyB,
                                   String nodeInstanceA, String nodeInstanceB) {
        this.mapInstanceId = mapInstanceId;
        this.nodeId = nodeId;
        this.partyA = partyA;
        this.partyB = partyB;
        this.nodeInstanceA = nodeInstanceA;
        this.nodeInstanceB = nodeInstanceB;
    }
}
