package crossspire.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable party directory entry. */
public final class PartyState {

    public static final String PHASE_STAGE_TRANSITION = "stage_transition";
    public static final String PHASE_MAP_ACTIVE = "map_active";
    public static final String PHASE_ACTIVE_NODE = "active_node";
    public static final String PHASE_MAP_COMPLETED = "map_completed";

    public final String partyId;
    public final String leaderId;
    public final List<String> memberIds;
    public final String mapPosition;
    public final String phaseStatus;
    public final String actId;
    public final String mapInstanceId;
    public final String nodeInstanceHostId;
    public final String activeNodeInstanceId;
    public final int partyRevision;

    /** Membership-only constructor used by tests and early join paths. */
    public PartyState(String partyId, List<String> memberIds, String mapPosition) {
        this(partyId, memberIds, mapPosition, PHASE_STAGE_TRANSITION, "", "", "", "", 0);
    }

    PartyState(String partyId, List<String> memberIds, String mapPosition,
               String phaseStatus, String actId, String mapInstanceId,
               String nodeInstanceHostId, String activeNodeInstanceId, int partyRevision) {
        this.partyId = partyId;
        List<String> sorted = new ArrayList<String>(memberIds);
        Collections.sort(sorted);
        this.memberIds = Collections.unmodifiableList(sorted);
        this.leaderId = sorted.isEmpty() ? "" : sorted.get(0);
        this.mapPosition = mapPosition != null ? mapPosition : "";
        this.phaseStatus = phaseStatus != null && !phaseStatus.isEmpty()
            ? phaseStatus : PHASE_STAGE_TRANSITION;
        this.actId = actId != null ? actId : "";
        this.mapInstanceId = mapInstanceId != null ? mapInstanceId : "";
        this.nodeInstanceHostId = nodeInstanceHostId != null ? nodeInstanceHostId : "";
        this.activeNodeInstanceId = activeNodeInstanceId != null ? activeNodeInstanceId : "";
        this.partyRevision = partyRevision;
    }
}
