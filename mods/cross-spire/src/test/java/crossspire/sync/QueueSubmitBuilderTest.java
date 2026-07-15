package crossspire.sync;

import crossspire.network.Protocol;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueueSubmitBuilderTest {

    @Test
    public void shouldBuildWithCardIdAndTarget() {
        Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build("Strike_R", "Cultist");
        assertNotNull(pkt);
        assertEquals("queue_submit", pkt.type);
        assertEquals("Strike_R", pkt.cardId);
        assertEquals("Cultist", pkt.gameTarget);
    }

    @Test
    public void shouldDefaultTargetToSelfWhenNull() {
        Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build("Defend_R", null);
        assertEquals("self", pkt.gameTarget);
    }

    @Test
    public void shouldSetSenderAsOwner() {
        Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build("Bash", "self");
        assertEquals(pkt.senderId, pkt.ownerId);
    }

    @Test
    public void shouldAssignTimestamp() {
        Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build("Strike_R", "monster_0");
        assertTrue("timestamp should be > 0", pkt.timestamp > 0);
    }
}
