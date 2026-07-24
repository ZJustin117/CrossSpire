package crossspire.party;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PartyMemberIdResolverTest {

    @Test
    public void resolvesUniqueShortIdWithinParty() {
        PartyState party = new PartyState("P0", Arrays.asList("565618f7-full", "2e032616-full"), "");

        assertEquals("565618f7-full", PartyMemberIdResolver.resolve(party, "565618f7"));
    }

    @Test
    public void rejectsAmbiguousOrForeignId() {
        PartyState party = new PartyState("P0", Arrays.asList("abcd1111-one", "abcd2222-two"), "");

        assertNull(PartyMemberIdResolver.resolve(party, "abcd"));
        assertNull(PartyMemberIdResolver.resolve(party, "missing"));
    }
}
