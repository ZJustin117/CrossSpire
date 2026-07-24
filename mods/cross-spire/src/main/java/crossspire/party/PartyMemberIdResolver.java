package crossspire.party;

/** Resolves a console-friendly unique player-ID prefix only within the selected party. */
public final class PartyMemberIdResolver {

    private PartyMemberIdResolver() {}

    public static String resolve(PartyState party, String idOrPrefix) {
        if (party == null || idOrPrefix == null || idOrPrefix.isEmpty()) return null;
        String resolved = null;
        for (String memberId : party.memberIds) {
            if (memberId.equals(idOrPrefix)) return memberId;
            if (memberId.startsWith(idOrPrefix)) {
                if (resolved != null) return null;
                resolved = memberId;
            }
        }
        return resolved;
    }
}
