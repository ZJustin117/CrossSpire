package crossspire.event;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FallbackNihResultPlannerTest {

    @Test
    public void buildsResultForApprovedFallbackOption() {
        Protocol.EventInterfacePayload iface = iface();
        Protocol.EventChoiceDecisionPayload decision = decision(0);

        Protocol.EventPlayerResultPayload result = FallbackNihResultPlanner.plan(iface, decision, "bob");

        assertNotNull(result);
        assertEquals("E1", result.eventInstanceId);
        assertEquals("P0", result.partyId);
        assertEquals("req-1", result.requestId);
        assertEquals("bob", result.playerId);
        assertNotNull(result.effects);
        assertTrue(result.effects.length >= 1);
        assertEquals("gain_gold", result.effects[0].kind);
    }

    @Test
    public void rejectsUnknownOrDisabledOption() {
        Protocol.EventInterfacePayload iface = iface();
        Protocol.EventChoiceDecisionPayload bad = decision(9);
        assertNull(FallbackNihResultPlanner.plan(iface, bad, "bob"));

        iface.options[0].enabled = false;
        iface.options[0].disabled = true;
        assertNull(FallbackNihResultPlanner.plan(iface, decision(0), "bob"));
    }

    @Test
    public void shouldApplyPersonalResultOnlyForLocalChooser() {
        Protocol.EventPlayerResultPayload result = new Protocol.EventPlayerResultPayload();
        result.playerId = "bob";
        result.effects = new Protocol.EffectDescription[0];
        assertTrue(EventPlayerResultApplyPlanner.shouldApplyLocally(result, "bob"));
        assertEquals(false, EventPlayerResultApplyPlanner.shouldApplyLocally(result, "alice"));
        assertEquals(false, EventPlayerResultApplyPlanner.shouldApplyLocally(null, "bob"));
    }

    private static Protocol.EventInterfacePayload iface() {
        Protocol.EventInterfacePayload payload = new Protocol.EventInterfacePayload();
        payload.eventInstanceId = "E1";
        payload.partyId = "P0";
        payload.eventId = "BigFish";
        payload.mode = "individual";
        Protocol.EventOptionInfo opt0 = new Protocol.EventOptionInfo();
        opt0.index = 0;
        opt0.enabled = true;
        opt0.text = "banana";
        Protocol.EventOptionInfo opt1 = new Protocol.EventOptionInfo();
        opt1.index = 1;
        opt1.enabled = true;
        opt1.text = "donut";
        payload.options = new Protocol.EventOptionInfo[] {opt0, opt1};
        return payload;
    }

    private static Protocol.EventChoiceDecisionPayload decision(int option) {
        Protocol.EventChoiceDecisionPayload d = new Protocol.EventChoiceDecisionPayload();
        d.eventInstanceId = "E1";
        d.partyId = "P0";
        d.requestId = "req-1";
        d.uiStep = "buttonEffect";
        d.optionIndex = option;
        return d;
    }
}
