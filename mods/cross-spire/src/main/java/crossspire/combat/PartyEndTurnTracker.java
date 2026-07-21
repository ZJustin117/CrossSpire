package crossspire.combat;

import crossspire.party.PartyState;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pure per-party end-turn readiness tracker; room membership is deliberately not consulted. */
public final class PartyEndTurnTracker {

    private final Map<String, Set<String>> readyByParty = new HashMap<String, Set<String>>();

    public synchronized boolean markReady(PartyState party, String playerId) {
        if (party == null || playerId == null || !party.memberIds.contains(playerId)) return false;
        Set<String> ready = readyFor(party.partyId);
        ready.add(playerId);
        return ready.containsAll(party.memberIds);
    }

    public synchronized int readyCount(String partyId) {
        return readyFor(partyId).size();
    }

    public synchronized boolean hasConsensus(PartyState party) {
        return party != null && readyFor(party.partyId).containsAll(party.memberIds);
    }

    public synchronized void clear(String partyId) {
        readyByParty.remove(partyId);
    }

    public synchronized void retainParties(Collection<PartyState> parties) {
        Set<String> active = new HashSet<String>();
        if (parties != null) for (PartyState party : parties) active.add(party.partyId);
        readyByParty.keySet().retainAll(active);
    }

    private Set<String> readyFor(String partyId) {
        Set<String> ready = readyByParty.get(partyId);
        if (ready == null) {
            ready = new HashSet<String>();
            readyByParty.put(partyId, ready);
        }
        return ready;
    }
}
