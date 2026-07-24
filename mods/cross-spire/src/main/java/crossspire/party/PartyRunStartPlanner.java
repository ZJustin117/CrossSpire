package crossspire.party;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure validation/build for coordinated dual-client run start (T7.7a / US-1a).
 * Does not touch STS engine state.
 */
public final class PartyRunStartPlanner {

    private PartyRunStartPlanner() {}

    public static boolean allMembersReady(Collection<String> memberIds,
                                          Set<String> readyPlayers) {
        if (memberIds == null || memberIds.isEmpty() || readyPlayers == null) return false;
        for (String id : memberIds) {
            if (id == null || id.isEmpty() || !readyPlayers.contains(id)) return false;
        }
        return true;
    }

    public static String rejectReason(Collection<String> memberIds,
                                      Set<String> readyPlayers,
                                      String requesterId,
                                      boolean alreadyStarted) {
        if (alreadyStarted) return "already_started";
        if (memberIds == null || memberIds.isEmpty()) return "no_party";
        if (requesterId == null || requesterId.isEmpty() || !memberIds.contains(requesterId)) {
            return "not_member";
        }
        if (!allMembersReady(memberIds, readyPlayers)) return "not_all_ready";
        return null;
    }

    public static Protocol.PartyRunStart build(String partyId,
                                               String seed,
                                               int act,
                                               String leaderId,
                                               String sourcePlayerId,
                                               Collection<String> memberIds,
                                               Map<String, String> characterByPlayer) {
        Protocol.PartyRunStart msg = new Protocol.PartyRunStart();
        msg.type = "party_run_start";
        msg.partyId = partyId != null && !partyId.isEmpty() ? partyId : PartyManager.DEFAULT_PARTY_ID;
        msg.seed = seed != null && !seed.isEmpty()
            ? seed
            : String.valueOf(System.currentTimeMillis() % 900000L + 100000L);
        msg.act = act > 0 ? act : 1;
        msg.leaderId = leaderId != null ? leaderId : "";
        msg.source = sourcePlayerId;
        List<Protocol.PartyRunMember> members = new ArrayList<Protocol.PartyRunMember>();
        if (memberIds != null) {
            for (String id : memberIds) {
                if (id == null || id.isEmpty()) continue;
                Protocol.PartyRunMember m = new Protocol.PartyRunMember();
                m.playerId = id;
                String ch = characterByPlayer != null ? characterByPlayer.get(id) : null;
                m.character = ch != null && !ch.isEmpty() ? ch.toUpperCase() : "IRONCLAD";
                members.add(m);
            }
        }
        msg.members = members.toArray(new Protocol.PartyRunMember[0]);
        return msg;
    }

    public static String characterFor(Protocol.PartyRunStart msg, String playerId) {
        if (msg == null || msg.members == null || playerId == null) return "IRONCLAD";
        for (Protocol.PartyRunMember m : msg.members) {
            if (m != null && playerId.equals(m.playerId) && m.character != null && !m.character.isEmpty()) {
                return m.character.toUpperCase();
            }
        }
        return "IRONCLAD";
    }
}
