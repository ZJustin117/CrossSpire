package crossspire.map;

import crossspire.party.PartyState;
import java.util.HashMap;
import java.util.Map;

/** RoomHost-side, party-scoped unanimous elections for map and node-instance roles. */
public final class PartyHostElectionTracker {

    private final Map<String, Map<String, String>> mapHostVotes = new HashMap<String, Map<String, String>>();
    private final Map<String, Map<String, String>> nodeInstanceHostVotes = new HashMap<String, Map<String, String>>();

    public synchronized boolean castMapHostVote(PartyState party, String voterId, String candidateId) {
        return cast(party, voterId, candidateId, mapHostVotes);
    }

    public synchronized boolean castNodeInstanceHostVote(PartyState party, String voterId, String candidateId) {
        return cast(party, voterId, candidateId, nodeInstanceHostVotes);
    }

    public synchronized String mapHostConsensus(PartyState party) {
        return consensus(party, mapHostVotes);
    }

    public synchronized String nodeInstanceHostConsensus(PartyState party) {
        return consensus(party, nodeInstanceHostVotes);
    }

    public synchronized void clearParty(String partyId) {
        mapHostVotes.remove(partyId);
        nodeInstanceHostVotes.remove(partyId);
    }

    private static boolean cast(PartyState party, String voterId, String candidateId,
                                Map<String, Map<String, String>> votesByParty) {
        if (party == null || !party.memberIds.contains(voterId) || !party.memberIds.contains(candidateId)) {
            return false;
        }
        Map<String, String> votes = votesByParty.get(party.partyId);
        if (votes == null) {
            votes = new HashMap<String, String>();
            votesByParty.put(party.partyId, votes);
        }
        votes.put(voterId, candidateId);
        return true;
    }

    private static String consensus(PartyState party, Map<String, Map<String, String>> votesByParty) {
        if (party == null) return null;
        Map<String, String> votes = votesByParty.get(party.partyId);
        if (votes == null || votes.size() != party.memberIds.size()) return null;
        String candidate = null;
        for (String memberId : party.memberIds) {
            String vote = votes.get(memberId);
            if (vote == null || (candidate != null && !candidate.equals(vote))) return null;
            candidate = vote;
        }
        return candidate;
    }
}
