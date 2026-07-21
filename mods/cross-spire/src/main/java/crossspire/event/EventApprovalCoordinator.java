package crossspire.event;

import crossspire.network.Protocol;
import crossspire.party.PartyState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * RoomHost-side event choice approval.
 * Individual mode: each valid request is approved once.
 * Voting mode aggregation is deferred to T7.5.
 */
public final class EventApprovalCoordinator {

    public static final String MODE_INDIVIDUAL = "individual";
    public static final String MODE_VOTING = "voting";

    public static final class Decision {
        public final boolean approved;
        public final String reason;
        public final Protocol.EventChoiceDecisionPayload payload;

        private Decision(boolean approved, String reason, Protocol.EventChoiceDecisionPayload payload) {
            this.approved = approved;
            this.reason = reason != null ? reason : "";
            this.payload = payload;
        }

        public static Decision approve(Protocol.EventChoiceDecisionPayload payload) {
            return new Decision(true, "", payload);
        }

        public static Decision reject(String reason, Protocol.EventChoiceDecisionPayload payload) {
            return new Decision(false, reason, payload);
        }
    }

    private static final class OpenEvent {
        final String eventInstanceId;
        final String partyId;
        final String resourceHash;
        final String mode;
        final Set<Integer> enabledOptions = new HashSet<Integer>();
        final Set<String> decidedRequestIds = new HashSet<String>();
        final Set<String> completedRequestIds = new HashSet<String>();

        OpenEvent(String eventInstanceId, String partyId, String resourceHash, String mode) {
            this.eventInstanceId = eventInstanceId;
            this.partyId = partyId;
            this.resourceHash = resourceHash != null ? resourceHash : "";
            this.mode = mode != null && !mode.isEmpty() ? mode : MODE_INDIVIDUAL;
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
            iface.resourceHash, iface.mode);
        for (Protocol.EventOptionInfo opt : iface.options) {
            if (opt == null) continue;
            boolean enabled = opt.enabled || !opt.disabled;
            if (enabled) open.enabledOptions.add(opt.index);
        }
        if (open.enabledOptions.isEmpty()) return false;
        eventsById.put(iface.eventInstanceId, open);
        return true;
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
            decision.reason = "voting_deferred";
            return Decision.reject(decision.reason, decision);
        }
        open.decidedRequestIds.add(request.requestId);
        decision.reason = "";
        return Decision.approve(decision);
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
        if (result.playerId != null && !result.playerId.isEmpty()
            && !result.playerId.equals(sourcePlayerId)) {
            return false;
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
