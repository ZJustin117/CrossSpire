package crossspire.event;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FallbackEventChoiceSessionTest {

    @Test
    public void blocksSecondChoiceUntilDecisionAndApprovesOnce() {
        FallbackEventChoiceSession session = new FallbackEventChoiceSession();
        session.open(iface("E1", 0, 1));

        Protocol.EventChoiceRequestPayload first = session.choose(1);
        assertNotNull(first);
        assertEquals("E1", first.eventInstanceId);
        assertEquals(1, first.optionIndex);
        assertNull(session.choose(0));

        Protocol.EventChoiceDecisionPayload wrong = decision(first, 0);
        assertFalse(session.approve(wrong));
        assertTrue(session.approve(decision(first, 1)));
        assertNull(session.pendingRequest());
    }

    @Test
    public void rejectionAllowsRetryWithNewRequestId() {
        FallbackEventChoiceSession session = new FallbackEventChoiceSession();
        session.open(iface("E1", 0));
        Protocol.EventChoiceRequestPayload first = session.choose(0);
        assertTrue(session.reject(decision(first, 0)));
        Protocol.EventChoiceRequestPayload retry = session.choose(0);
        assertNotNull(retry);
        assertFalse(first.requestId.equals(retry.requestId));
    }

    private static Protocol.EventInterfacePayload iface(String id, int... enabled) {
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventInstanceId = id;
        iface.partyId = "P0";
        iface.resourceHash = "hash-a";
        iface.options = new Protocol.EventOptionInfo[enabled.length];
        for (int i = 0; i < enabled.length; i++) {
            Protocol.EventOptionInfo opt = new Protocol.EventOptionInfo();
            opt.index = enabled[i];
            opt.enabled = true;
            iface.options[i] = opt;
        }
        return iface;
    }

    private static Protocol.EventChoiceDecisionPayload decision(
        Protocol.EventChoiceRequestPayload request, int option) {
        Protocol.EventChoiceDecisionPayload decision = new Protocol.EventChoiceDecisionPayload();
        decision.eventInstanceId = request.eventInstanceId;
        decision.partyId = request.partyId;
        decision.requestId = request.requestId;
        decision.uiStep = request.uiStep;
        decision.optionIndex = option;
        return decision;
    }
}
