package crossspire;

import crossspire.ui.ServerPicker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrossSpireModTest {

    private boolean wasHost;

    @Before
    public void setUp() {
        wasHost = ServerPicker.isRoomHost;
    }

    @After
    public void tearDown() {
        ServerPicker.isRoomHost = wasHost;
    }

    @Test
    public void sendShouldNotThrowWhenNoConnection() {
        CrossSpireMod.connectionManager = null;
        CrossSpireMod.send("{\"type\":\"test\"}");
    }

    @Test
    public void sendAsRoomHostShouldNotThrow() {
        CrossSpireMod.connectionManager = null;
        ServerPicker.isRoomHost = true;
        CrossSpireMod.send("{\"type\":\"ping\"}");
    }

    @Test
    public void sendAsClientShouldNotThrow() {
        CrossSpireMod.connectionManager = null;
        CrossSpireMod.hostId = "fake-host";
        ServerPicker.isRoomHost = false;
        CrossSpireMod.send("{\"type\":\"ping\"}");
    }

    @Test
    public void isConnectedReturnsFalseWhenNoP2P() {
        CrossSpireMod.connectionManager = null;
        assertFalse(CrossSpireMod.isConnected());
    }
}
