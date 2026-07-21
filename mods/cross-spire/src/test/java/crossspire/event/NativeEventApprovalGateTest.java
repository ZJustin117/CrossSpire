package crossspire.event;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NativeEventApprovalGateTest {

    @Test
    public void blocksUntilMatchingApprovalThenPermitsExactlyOnce() {
        NativeEventApprovalGate gate = new NativeEventApprovalGate();
        Object event = new Object();
        gate.bind(event, iface("E1", "hash-a"));

        NativeEventApprovalGate.Attempt first = gate.beforeButtonEffect(event, 1);
        assertFalse(first.execute);
        assertNotNull(first.request);
        assertEquals("E1", first.request.eventInstanceId);
        assertEquals(1, first.request.optionIndex);

        assertFalse(gate.approve(decision(first.request, 0)));
        assertTrue(gate.approve(decision(first.request, 1)));
        assertTrue(gate.beforeButtonEffect(event, 1).execute);
        assertFalse(gate.beforeButtonEffect(event, 1).execute);
    }

    @Test
    public void rejectionClearsPendingChoiceWithoutExecutingIt() {
        NativeEventApprovalGate gate = new NativeEventApprovalGate();
        Object event = new Object();
        gate.bind(event, iface("E1", "hash-a"));
        NativeEventApprovalGate.Attempt first = gate.beforeButtonEffect(event, 0);

        assertTrue(gate.reject(decision(first.request, 0)));
        NativeEventApprovalGate.Attempt retry = gate.beforeButtonEffect(event, 0);

        assertFalse(retry.execute);
        assertNotNull(retry.request);
        assertFalse(first.request.requestId.equals(retry.request.requestId));
    }

    @Test
    public void passesThroughUnboundEventsAndRejectsDisabledOptions() {
        NativeEventApprovalGate gate = new NativeEventApprovalGate();
        assertTrue(gate.beforeButtonEffect(new Object(), 0).execute);

        Object event = new Object();
        Protocol.EventInterfacePayload iface = iface("E1", "hash-a");
        iface.options[0].enabled = false;
        iface.options[0].disabled = true;
        gate.bind(event, iface);
        assertFalse(gate.beforeButtonEffect(event, 0).execute);
        assertEquals(null, gate.beforeButtonEffect(event, 0).request);
    }

    private static Protocol.EventInterfacePayload iface(String id, String hash) {
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventInstanceId = id;
        iface.partyId = "P0";
        iface.resourceHash = hash;
        Protocol.EventOptionInfo first = new Protocol.EventOptionInfo();
        first.index = 0;
        first.enabled = true;
        Protocol.EventOptionInfo second = new Protocol.EventOptionInfo();
        second.index = 1;
        second.enabled = true;
        iface.options = new Protocol.EventOptionInfo[] {first, second};
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
