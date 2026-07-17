package crossspire.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class StarConnectionManagerTest {

    @Test
    public void shouldUseExplicitNetworkConfiguration() {
        StarConnectionManager manager = new StarConnectionManager(60000, "127.0.0.1");

        assertEquals(60000, manager.getPort());
        assertEquals("127.0.0.1", manager.getAdvertisedIp());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidListenPort() {
        new StarConnectionManager(65536, "127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBlankAdvertisedIp() {
        new StarConnectionManager(54321, " ");
    }

    @Test
    public void shouldParseValidPort() {
        assertEquals(54321, StarConnectionManager.parsePort("54321"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonNumericPort() {
        StarConnectionManager.parsePort("not-a-port");
    }
}
