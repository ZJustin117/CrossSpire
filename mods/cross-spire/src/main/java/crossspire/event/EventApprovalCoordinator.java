package crossspire.event;

import crossspire.network.Protocol;
import crossspire.party.PartyState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RoomHost-side event choice approval.
 * Individual mode: each valid request is approved once.
 * Voting mode: unanimous party option index required before approvals are released.
 */
public final class EventApprovalCoordinator {

    public static final String MODE_INDIVIDUAL = "individual";
    public static final String MODE_VOTING = "voting";

    public static final class Decision {
        public final boolean approved;
        public final String reason;
        public final Protocol.EventChoiceDecisionPayload payload;
        /** Voting consensus may approve every member's pending request at once. */
        public final List<Protocol.EventChoiceDecisionPayload> approvals;

        private Decision(boolean approved, String reason, Protocol.EventChoiceDecisionPayload payload,
                         List<Protocol.EventChoiceDecisionPayload> approvals) {
            this.approved = approved;
            this.reason = reason != null ? reason : "";
            this.payload = payload;
            this.approvals = approvals != null
                ? Collections.unmodifiableList(new ArrayList<Protocol.EventChoiceDecisionPayload>(approvals))
                : Collections.<Protocol.EventChoiceDecisionPayload>emptyList();
        }

        public static Decision approve(Protocol.EventChoiceDecisionPayload payload) {
            List<Protocol.EventChoiceDecisionPayload> one = new ArrayList<Protocol.EventChoiceDecisionPayload>();
            one.add(payload);
            return new Decision(true, "", payload, one);
        }

        public static Decision approveAll(List<Protocol.EventChoiceDecisionPayload> approvals) {
            Protocol.EventChoiceDecisionPayload first = approvals.isEmpty() ? null : approvals.get(0);
            return new Decision(true, "", first, approvals);
        }

        public static Decision reject(String reason, Protocol.EventChoiceDecisionPayload payload) {
            return new Decision(false, reason, payload, null);
        }
    }

    private static final class OpenEvent {
        final String eventInstanceId;
        final String partyId;
        final String resourceHash;
        final String mode;
        final Protocol.EventInterfacePayload iface;
        final Set<Integer> enabledOptions = new HashSet<Integer>();
        final Set<String> decidedRequestIds = new HashSet<String>();
        final Set<String> completedRequestIds = new HashSet<String>();
        final Map<String, String> chooserByRequestId = new HashMap<String, String>();
        final Map<String, Integer> optionByRequestId = new HashMap<String, Integer>();
        /** Voting: latest option index per member. */
        final Map<String, Integer> voteByPlayer = new HashMap<String, Integer>();
        /** Voting: latest request payload per member. */
        final Map<String, Protocol.EventChoiceRequestPayload> voteRequestByPlayer =
            new HashMap<String, Protocol.EventChoiceRequestPayload>();
        boolean votingReleased;

        OpenEvent(String eventInstanceId, String partyId, String resourceHash, String mode,
                  Protocol.EventInterfacePayload iface) {
            this.eventInstanceId = eventInstanceId;
            this.partyId = partyId;
            this.resourceHash = resourceHash != null ? resourceHash : "";
            this.mode = mode != null && !mode.isEmpty() ? mode : MODE_INDIVIDUAL;
            this.iface = iface;
        }
    }

    private final Map<String, OpenEvent> eventsById = new HashMap<String, OpenEvent>();

    public synchronized boolean registerInterface(Protocol.EventInterfacePayload iface, PartyState party) {
        if (iface == null || party == null
            || empty(iface.eventInstanceId) || empty(iface.partyId)
            || !iface.partyId.equals(party.partyId)
            || iface.options == null || iface.options.length == 0) {
            return false;
        }
        if (eventsById.containsKey(iface.eventInstanceId)) return false;
        OpenEvent open = new OpenEvent(iface.eventInstanceId, iface.partyId,
            iface.resourceHash, iface.mode, iface);
        for (Protocol.EventOptionInfo opt : iface.options) {
            if (opt == null) continue;
            boolean enabled = opt.enabled || !opt.disabled;
            if (enabled) open.enabledOptions.add(opt.index);
        }
        if (open.enabledOptions.isEmpty()) return false;
        eventsById.put(iface.eventInstanceId, open);
        return true;
    }

    public synchronized Protocol.EventInterfacePayload getInterface(String eventInstanceId) {
        OpenEvent open = eventsById.get(eventInstanceId);
        return open != null ? open.iface : null;
    }

    public synchronized String chooserForRequest(String eventInstanceId, String requestId) {
        OpenEvent open = eventsById.get(eventInstanceId);
        if (open == null || requestId == null) return null;
        return open.chooserByRequestId.get(requestId);
    }

    public synchronized Integer optionForRequest(String eventInstanceId, String requestId) {
        OpenEvent open = eventsById.get(eventInstanceId);
        if (open == null || requestId == null) return null;
        return open.optionByRequestId.get(requestId);
    }

    public synchronized Map<String, Integer> votes(String eventInstanceId) {
        OpenEvent open = eventsById.get(eventInstanceId);
        if (open == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(new HashMap<String, Integer>(open.voteByPlayer));
    }

    public synchronized Decision decide(PartyState party, String sourcePlayerId,
                                        Protocol.EventChoiceRequestPayload request) {
        Protocol.EventChoiceDecisionPayload decision = new Protocol.EventChoiceDecisionPayload();
        if (request != null) {
            decision.eventInstanceId = request.eventInstanceId;
            decision.partyId = request.partyId;
            decision.requestId = request.requestId;
            decision.uiStep = request.uiStep;
            decision.optionIndex = request.optionIndex;
        }
        if (party == null || empty(sourcePlayerId) || request == null
            || empty(request.eventInstanceId) || empty(request.requestId)
            || empty(request.uiStep)) {
            decision.reason = "invalid_request";
            return Decision.reject(decision.reason, decision);
        }
        OpenEvent open = eventsById.get(request.eventInstanceId);
        if (open == null) {
            decision.reason = "unknown_event";
            return Decision.reject(decision.reason, decision);
        }
        if (!open.partyId.equals(party.partyId) || !open.partyId.equals(request.partyId)) {
            decision.reason = "party_mismatch";
            return Decision.reject(decision.reason, decision);
        }
        if (!party.memberIds.contains(sourcePlayerId)) {
            decision.reason = "not_member";
            return Decision.reject(decision.reason, decision);
        }
        if (open.decidedRequestIds.contains(request.requestId)) {
            decision.reason = "duplicate_request";
            return Decision.reject(decision.reason, decision);
        }
        if (!open.resourceHash.isEmpty()
            && request.resourceHash != null
            && !request.resourceHash.isEmpty()
            && !open.resourceHash.equals(request.resourceHash)) {
            decision.reason = "hash_mismatch";
            return Decision.reject(decision.reason, decision);
        }
        if (!open.enabledOptions.contains(request.optionIndex)) {
            decision.reason = "option_disabled";
            return Decision.reject(decision.reason, decision);
        }
        if (MODE_VOTING.equals(open.mode)) {
            return decideVoting(open, party, sourcePlayerId, request, decision);
        }
        open.decidedRequestIds.add(request.requestId);
        open.chooserByRequestId.put(request.requestId, sourcePlayerId);
        open.optionByRequestId.put(request.requestId, Integer.valueOf(request.optionIndex));
        decision.reason = "";
        return Decision.approve(decision);
    }

    private Decision decideVoting(OpenEvent open, PartyState party, String sourcePlayerId,
                                  Protocol.EventChoiceRequestPayload request,
                                  Protocol.EventChoiceDecisionPayload decision) {
        if (open.votingReleased) {
            decision.reason = "voting_closed";
            return Decision.reject(decision.reason, decision);
        }
        open.voteByPlayer.put(sourcePlayerId, Integer.valueOf(request.optionIndex));
        open.voteRequestByPlayer.put(sourcePlayerId, request);
        Integer consensus = votingConsensus(open, party);
        if (consensus == null) {
            decision.reason = "voting_pending";
            return Decision.reject(decision.reason, decision);
        }
        List<Protocol.EventChoiceDecisionPayload> approvals =
            new ArrayList<Protocol.EventChoiceDecisionPayload>();
        for (String memberId : party.memberIds) {
            Protocol.EventChoiceRequestPayload memberRequest = open.voteRequestByPlayer.get(memberId);
            if (memberRequest == null) continue;
            Protocol.EventChoiceDecisionPayload payload = new Protocol.EventChoiceDecisionPayload();
            payload.eventInstanceId = memberRequest.eventInstanceId;
            payload.partyId = memberRequest.partyId;
            payload.requestId = memberRequest.requestId;
            payload.uiStep = memberRequest.uiStep;
            payload.optionIndex = consensus.intValue();
            payload.reason = "";
            open.decidedRequestIds.add(memberRequest.requestId);
            open.chooserByRequestId.put(memberRequest.requestId, memberId);
            open.optionByRequestId.put(memberRequest.requestId, consensus);
            approvals.add(payload);
        }
        open.votingReleased = true;
        return Decision.approveAll(approvals);
    }

    private static Integer votingConsensus(OpenEvent open, PartyState party) {
        if (open.voteByPlayer.size() != party.memberIds.size()) return null;
        Integer option = null;
        for (String memberId : party.memberIds) {
            Integer vote = open.voteByPlayer.get(memberId);
            if (vote == null) return null;
            if (option != null && !option.equals(vote)) return null;
            option = vote;
        }
        return option;
    }

    public synchronized boolean acceptPlayerResult(PartyState party, String sourcePlayerId,
                                                   Protocol.EventPlayerResultPayload result) {
        if (party == null || empty(sourcePlayerId) || result == null
            || empty(result.eventInstanceId) || empty(result.requestId)) {
            return false;
        }
        OpenEvent open = eventsById.get(result.eventInstanceId);
        if (open == null) return false;
        if (!open.partyId.equals(party.partyId) || !open.partyId.equals(result.partyId)) return false;
        if (!party.memberIds.contains(sourcePlayerId)) return false;
        if (!open.decidedRequestIds.contains(result.requestId)) return false;
        if (open.completedRequestIds.contains(result.requestId)) return false;
        // Result may be emitted by the chooser or by the party's NodeInstanceHost on their behalf.
        String expectedChooser = open.chooserByRequestId.get(result.requestId);
        if (result.playerId != null && !result.playerId.isEmpty()) {
            if (expectedChooser != null && !expectedChooser.equals(result.playerId)) return false;
            boolean sourceIsChooser = result.playerId.equals(sourcePlayerId);
            boolean sourceIsNih = party.nodeInstanceHostId != null
                && party.nodeInstanceHostId.equals(sourcePlayerId);
            if (!sourceIsChooser && !sourceIsNih) return false;
        }
        open.completedRequestIds.add(result.requestId);
        return true;
    }

    public synchronized boolean hasEvent(String eventInstanceId) {
        return eventInstanceId != null && eventsById.containsKey(eventInstanceId);
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
