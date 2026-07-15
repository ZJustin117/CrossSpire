package crossspire.network;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoomHostTest {

    @Test
    public void newRoomHostHasNoPlayers() {
        RoomHost host = new RoomHost("host-123");
        assertEquals(0, host.getPlayerCount());
    }

    @Test
    public void playerIdIsAsAssigned() {
        RoomHost host = new RoomHost("host-123");
        assertEquals("host-123", host.getHostPlayerId());
    }

    @Test
    public void addPlayerIncreasesCount() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("player-2");
        assertEquals(1, host.getPlayerCount());
        assertTrue(host.hasPlayer("player-2"));
    }

    @Test
    public void removePlayerDecreasesCount() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("player-2");
        host.removePlayer("player-2");
        assertEquals(0, host.getPlayerCount());
        assertFalse(host.hasPlayer("player-2"));
    }

    @Test
    public void getOtherPlayersExcludesSelf() {
        RoomHost host = new RoomHost("host-123");
        host.addPlayer("host-123");
        host.addPlayer("player-2");
        host.addPlayer("player-3");
        List<String> others = host.getOtherPlayers("host-123");
        assertEquals(2, others.size());
        assertTrue(others.contains("player-2"));
        assertTrue(others.contains("player-3"));
        assertFalse(others.contains("host-123"));
    }
}
