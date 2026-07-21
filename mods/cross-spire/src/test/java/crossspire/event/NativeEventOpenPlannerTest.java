package crossspire.event;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NativeEventOpenPlannerTest {

    @Test
    public void choosesNativeOnlyWhenClassAndHashMatch() {
        Protocol.EventInterfacePayload iface = iface("E1", "hash-a");

        assertEquals(NativeEventOpenPlanner.Mode.NATIVE,
            NativeEventOpenPlanner.mode(iface, true, "hash-a"));
        assertEquals(NativeEventOpenPlanner.Mode.FALLBACK,
            NativeEventOpenPlanner.mode(iface, false, "hash-a"));
        assertEquals(NativeEventOpenPlanner.Mode.FALLBACK,
            NativeEventOpenPlanner.mode(iface, true, "other"));
        assertEquals(NativeEventOpenPlanner.Mode.FALLBACK,
            NativeEventOpenPlanner.mode(iface, true, ""));
    }

    @Test
    public void rejectsIncompleteInterfacesAsFallback() {
        Protocol.EventInterfacePayload incomplete = iface("E1", "hash-a");
        incomplete.eventClass = "";
        assertEquals(NativeEventOpenPlanner.Mode.FALLBACK,
            NativeEventOpenPlanner.mode(incomplete, true, "hash-a"));
        assertEquals(NativeEventOpenPlanner.Mode.FALLBACK,
            NativeEventOpenPlanner.mode(null, true, "hash-a"));
    }

    private static Protocol.EventInterfacePayload iface(String id, String hash) {
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventInstanceId = id;
        iface.partyId = "P0";
        iface.eventClass = "com.example.Event";
        iface.eventId = "Event";
        iface.resourceHash = hash;
        Protocol.EventOptionInfo option = new Protocol.EventOptionInfo();
        option.index = 0;
        option.text = "ok";
        option.enabled = true;
        iface.options = new Protocol.EventOptionInfo[] {option};
        return iface;
    }
}
