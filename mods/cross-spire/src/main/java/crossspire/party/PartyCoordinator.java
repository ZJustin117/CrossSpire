package crossspire.party;

/** Party-scoped authorization used by transport routing and gameplay coordinators. */
public final class PartyCoordinator {

    private PartyCoordinator() {}

    public static boolean isMember(PartyManager manager, String partyId, String playerId) {
        PartyState party = party(manager, partyId);
        return party != null && playerId != null && party.memberIds.contains(playerId);
    }

    public static boolean isLeader(PartyManager manager, String partyId, String playerId) {
        PartyState party = party(manager, partyId);
        return party != null && playerId != null && playerId.equals(party.leaderId);
    }

    public static String leaderId(PartyManager manager, String partyId) {
        PartyState party = party(manager, partyId);
        return party != null ? party.leaderId : "";
    }

    private static PartyState party(PartyManager manager, String partyId) {
        if (manager == null) return null;
        String effectivePartyId = partyId == null || partyId.isEmpty()
            ? PartyManager.DEFAULT_PARTY_ID : partyId;
        return manager.getParty(effectivePartyId);
    }
}
