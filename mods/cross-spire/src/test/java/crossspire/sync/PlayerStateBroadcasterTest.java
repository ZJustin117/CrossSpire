package crossspire.sync;

import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerStateBroadcasterTest {

    @Test
    public void shouldBuildValidPlayerStateMessage() {
        Protocol.PlayerStateMessage msg = PlayerStateBroadcaster.build(80, 80, 0, 3, 99, "IRONCLAD");
        assertEquals("player_state", msg.type);
        assertEquals(80, msg.player.hp);
        assertEquals(80, msg.player.maxHp);
        assertEquals(0, msg.player.block);
        assertEquals(3, msg.player.energy);
        assertEquals(99, msg.player.gold);
        assertEquals("IRONCLAD", msg.player.characterClass);
    }

    @Test
    public void shouldSetSourceToCurrentPlayerId() {
        String prev = CrossSpireMod.playerId;
        CrossSpireMod.playerId = "test-player";
        try {
            Protocol.PlayerStateMessage msg = PlayerStateBroadcaster.build(50, 80, 5, 2, 0, "X");
            assertEquals("test-player", msg.source);
        } finally {
            CrossSpireMod.playerId = prev;
        }
    }
}
