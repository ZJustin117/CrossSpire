package crossspire.party;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure room party directory. Transport and gameplay routing are added in T7.2. */
public final class PartyManager {

    public static final String DEFAULT_PARTY_ID = "P0";

    public static final class JoinRequest {
        public final String requestId;
        public final String playerId;
        public final String partyId;
        public String reason;

        private JoinRequest(String requestId, String playerId, String partyId) {
            this.requestId = requestId;
            this.playerId = playerId;
            this.partyId = partyId;
        }
    }

    private final Map<String, PartyState> parties = new LinkedHashMap<String, PartyState>();
    private final Map<String, String> partyByPlayer = new LinkedHashMap<String, String>();
    private final Map<String, JoinRequest> joinRequests = new LinkedHashMap<String, JoinRequest>();
    private int nextPartyId = 1;

    public synchronized void initializeDefaultParty(Collection<String> members, String mapPosition) {
        parties.clear();
        partyByPlayer.clear();
        joinRequests.clear();
        nextPartyId = 1;
        putParty(DEFAULT_PARTY_ID, uniqueMembers(members), mapPosition,
            PartyState.PHASE_STAGE_TRANSITION, "", "", "", "", 0);
    }

    public synchronized PartyState getParty(String partyId) {
        return parties.get(partyId);
    }

    /** Adds a newly connected player to P0 without discarding existing party splits. */
    public synchronized boolean addPlayerToDefaultParty(String playerId) {
        if (isEmpty(playerId) || partyByPlayer.containsKey(playerId)) return false;
        PartyState defaultParty = parties.get(DEFAULT_PARTY_ID);
        if (defaultParty == null) return false;
        List<String> members = new ArrayList<String>(defaultParty.memberIds);
        members.add(playerId);
        replaceMembers(defaultParty, members);
        return true;
    }

    public synchronized String getPartyIdForPlayer(String playerId) {
        return partyByPlayer.get(playerId);
    }

    public synchronized List<PartyState> snapshot() {
        return Collections.unmodifiableList(new ArrayList<PartyState>(parties.values()));
    }

    /** Replaces the directory only when the complete incoming snapshot is valid. */
    public synchronized boolean replaceSnapshot(Collection<PartyState> incoming) {
        if (incoming == null) return false;
        Map<String, PartyState> replacement = new LinkedHashMap<String, PartyState>();
        Map<String, String> replacementByPlayer = new LinkedHashMap<String, String>();
        for (PartyState party : incoming) {
            if (party == null || isEmpty(party.partyId) || party.memberIds.isEmpty()
                || replacement.put(party.partyId, party) != null) {
                return false;
            }
            for (String member : party.memberIds) {
                if (isEmpty(member) || replacementByPlayer.put(member, party.partyId) != null) {
                    return false;
                }
            }
            if (!party.leaderId.equals(party.memberIds.get(0))) return false;
        }
        parties.clear();
        parties.putAll(replacement);
        partyByPlayer.clear();
        partyByPlayer.putAll(replacementByPlayer);
        joinRequests.clear();
        return true;
    }

    public synchronized PartyState leave(String playerId) {
        String currentPartyId = partyByPlayer.get(playerId);
        PartyState current = parties.get(currentPartyId);
        if (current == null || current.memberIds.size() <= 1) return null;

        List<String> remaining = new ArrayList<String>(current.memberIds);
        remaining.remove(playerId);
        replaceMembers(current, remaining);
        String soloId = nextAvailablePartyId();
        putParty(soloId, Arrays.asList(playerId), current.mapPosition,
            current.phaseStatus, current.actId, current.mapInstanceId,
            "", current.activeNodeInstanceId, current.partyRevision + 1);
        return parties.get(soloId);
    }

    public synchronized boolean requestJoin(String requestId, String playerId, String targetPartyId) {
        if (isEmpty(requestId) || isEmpty(playerId) || joinRequests.containsKey(requestId)
            || !parties.containsKey(targetPartyId) || !partyByPlayer.containsKey(playerId)
            || targetPartyId.equals(partyByPlayer.get(playerId))) {
            return false;
        }
        joinRequests.put(requestId, new JoinRequest(requestId, playerId, targetPartyId));
        return true;
    }

    public synchronized JoinRequest getJoinRequest(String requestId) {
        return joinRequests.get(requestId);
    }

    public synchronized boolean approveJoinRequest(String requestId, String approverId) {
        JoinRequest request = joinRequests.get(requestId);
        PartyState target = request != null ? parties.get(request.partyId) : null;
        if (target == null || !target.leaderId.equals(approverId)) return false;
        movePlayer(request.playerId, target.partyId, target.mapPosition);
        joinRequests.remove(requestId);
        return true;
    }

    public synchronized boolean rejectJoinRequest(String requestId, String approverId, String reason) {
        JoinRequest request = joinRequests.get(requestId);
        PartyState target = request != null ? parties.get(request.partyId) : null;
        if (target == null || !target.leaderId.equals(approverId)) return false;
        request.reason = reason != null ? reason : "";
        return true;
    }

    public synchronized void removePlayer(String playerId) {
        String partyId = partyByPlayer.remove(playerId);
        PartyState party = parties.get(partyId);
        if (party != null) {
            List<String> remaining = new ArrayList<String>(party.memberIds);
            remaining.remove(playerId);
            if (remaining.isEmpty()) parties.remove(partyId);
            else replaceMembers(party, remaining);
        }
        List<String> expired = new ArrayList<String>();
        for (JoinRequest request : joinRequests.values()) {
            if (playerId.equals(request.playerId)
                || (parties.get(request.partyId) != null
                    && playerId.equals(parties.get(request.partyId).leaderId))) {
                expired.add(request.requestId);
            }
        }
        for (String requestId : expired) joinRequests.remove(requestId);
    }

    /**
     * Binds the party to a registered map at the start node.
     * Clears any prior NodeInstanceHost (must re-elect after create/join map).
     */
    public synchronized PartyState bindMap(String partyId, String mapInstanceId,
                                           String startNodeId, String actId) {
        PartyState current = parties.get(partyId);
        if (current == null || isEmpty(mapInstanceId) || isEmpty(startNodeId)) return null;
        putParty(partyId, current.memberIds, startNodeId,
            PartyState.PHASE_MAP_ACTIVE,
            actId != null ? actId : current.actId,
            mapInstanceId, "", "", current.partyRevision + 1);
        return parties.get(partyId);
    }

    /** Records the unanimously elected NodeInstanceHost for a map-bound party. */
    public synchronized PartyState setNodeInstanceHost(String partyId, String nodeInstanceHostId) {
        PartyState current = parties.get(partyId);
        if (current == null || isEmpty(nodeInstanceHostId)
            || isEmpty(current.mapInstanceId)
            || !current.memberIds.contains(nodeInstanceHostId)) {
            return null;
        }
        putParty(partyId, current.memberIds, current.mapPosition,
            current.phaseStatus, current.actId, current.mapInstanceId,
            nodeInstanceHostId, current.activeNodeInstanceId, current.partyRevision + 1);
        return parties.get(partyId);
    }

    /** Advances the party map cursor after a validated node entry. */
    public synchronized PartyState enterNode(String partyId, String nodeId, String nodeInstanceId) {
        PartyState current = parties.get(partyId);
        if (current == null || isEmpty(nodeId) || isEmpty(current.mapInstanceId)) return null;
        putParty(partyId, current.memberIds, nodeId,
            PartyState.PHASE_ACTIVE_NODE, current.actId, current.mapInstanceId,
            current.nodeInstanceHostId, nodeInstanceId != null ? nodeInstanceId : "",
            current.partyRevision + 1);
        return parties.get(partyId);
    }

    private void movePlayer(String playerId, String targetPartyId, String targetPosition) {
        String sourcePartyId = partyByPlayer.get(playerId);
        PartyState source = parties.get(sourcePartyId);
        if (source != null) {
            List<String> remaining = new ArrayList<String>(source.memberIds);
            remaining.remove(playerId);
            if (remaining.isEmpty()) parties.remove(sourcePartyId);
            else replaceMembers(source, remaining);
        }
        PartyState target = parties.get(targetPartyId);
        List<String> members = new ArrayList<String>(target.memberIds);
        members.add(playerId);
        putParty(targetPartyId, members, targetPosition != null ? targetPosition : target.mapPosition,
            target.phaseStatus, target.actId, target.mapInstanceId,
            target.nodeInstanceHostId, target.activeNodeInstanceId, target.partyRevision + 1);
    }

    private void replaceMembers(PartyState current, Collection<String> members) {
        putParty(current.partyId, members, current.mapPosition,
            current.phaseStatus, current.actId, current.mapInstanceId,
            current.nodeInstanceHostId, current.activeNodeInstanceId, current.partyRevision + 1);
    }

    private void putParty(String partyId, Collection<String> members, String mapPosition,
                          String phaseStatus, String actId, String mapInstanceId,
                          String nodeInstanceHostId, String activeNodeInstanceId, int partyRevision) {
        PartyState party = new PartyState(partyId, new ArrayList<String>(members), mapPosition,
            phaseStatus, actId, mapInstanceId, nodeInstanceHostId, activeNodeInstanceId, partyRevision);
        parties.put(partyId, party);
        for (String member : party.memberIds) partyByPlayer.put(member, partyId);
    }

    private String nextAvailablePartyId() {
        String partyId;
        do { partyId = "P" + nextPartyId++; } while (parties.containsKey(partyId));
        return partyId;
    }

    private static List<String> uniqueMembers(Collection<String> members) {
        if (members == null) return Collections.emptyList();
        Set<String> unique = new LinkedHashSet<String>();
        for (String member : members) if (!isEmpty(member)) unique.add(member);
        return new ArrayList<String>(unique);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
