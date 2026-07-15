package crossspire.combat;

import crossspire.network.Protocol;
import static org.junit.Assert.*;
import org.junit.Test;

public class CentralQueueManagerTest {

    @Test
    public void shouldInsertAndSortByTimestamp() {
        CentralQueueManager mgr = new CentralQueueManager();

        Protocol.QueueSubmitMessage pkt1 = new Protocol.QueueSubmitMessage();
        pkt1.cardId = "Strike_R";
        pkt1.timestamp = 100;
        pkt1.senderId = "alice";
        pkt1.source = "alice";
        pkt1.seq = 1;

        Protocol.QueueSubmitMessage pkt2 = new Protocol.QueueSubmitMessage();
        pkt2.cardId = "Defend_G";
        pkt2.timestamp = 200;
        pkt2.senderId = "bob";
        pkt2.source = "bob";
        pkt2.seq = 2;

        Protocol.QueueSubmitMessage pkt3 = new Protocol.QueueSubmitMessage();
        pkt3.cardId = "Bash";
        pkt3.timestamp = 100;
        pkt3.senderId = "bob";
        pkt3.source = "bob";
        pkt3.seq = 3;

        mgr.onQueueSubmit(pkt2);
        mgr.onQueueSubmit(pkt1);
        mgr.onQueueSubmit(pkt3);

        assertEquals(3, mgr.size());
        Protocol.QueueEntry[] entries = mgr.getEntries();

        assertEquals("Strike_R", entries[0].cardId);
        assertEquals("Bash", entries[1].cardId);
        assertEquals("Defend_G", entries[2].cardId);
    }

    @Test
    public void shouldDequeueHead() {
        CentralQueueManager mgr = new CentralQueueManager();

        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.cardId = "Strike_R";
        pkt.timestamp = 100;
        pkt.senderId = "alice";
        pkt.source = "alice";
        pkt.seq = 1;

        mgr.onQueueSubmit(pkt);
        assertEquals(1, mgr.size());

        Protocol.QueueSubmitMessage head = mgr.dequeue();
        assertNotNull(head);
        assertEquals("Strike_R", head.cardId);
        assertEquals(0, mgr.size());
    }

    @Test
    public void shouldSetEntryStatus() {
        CentralQueueManager mgr = new CentralQueueManager();

        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.cardId = "Strike_R";
        pkt.timestamp = 100;
        pkt.senderId = "alice";
        pkt.source = "alice";
        pkt.seq = 1;
        pkt.ownerId = "alice";

        mgr.onQueueSubmit(pkt);
        assertEquals("executing", mgr.getEntries()[0].status);

        String pid = mgr.getEntries()[0].packetId;
        mgr.setExecuting(pkt.cardId);

        mgr.markDone(pid);
        assertEquals(0, mgr.size());
    }

    @Test
    public void shouldNotDequeueWhenEmpty() {
        CentralQueueManager mgr = new CentralQueueManager();
        assertNull(mgr.dequeue());
        assertEquals(0, mgr.size());
    }

    @Test
    public void shouldEnqueueWithPacketId() {
        CentralQueueManager mgr = new CentralQueueManager();

        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.cardId = "Strike_R";
        pkt.timestamp = 100;
        pkt.senderId = "alice";
        pkt.source = "alice";
        pkt.seq = 1;
        pkt.ownerId = "alice";

        mgr.onQueueSubmit(pkt);

        Protocol.QueueEntry[] entries = mgr.getEntries();
        assertNotNull(entries[0].packetId);
        assertTrue(entries[0].packetId.contains("/"));
    }

    @Test
    public void shouldDedupRepeatedSubmits() {
        CentralQueueManager mgr = new CentralQueueManager();

        Protocol.QueueSubmitMessage pkt = new Protocol.QueueSubmitMessage();
        pkt.cardId = "Strike_R";
        pkt.timestamp = 100;
        pkt.senderId = "alice";
        pkt.source = "alice";
        pkt.seq = 1;
        pkt.ownerId = "alice";

        mgr.onQueueSubmit(pkt);
        mgr.onQueueSubmit(pkt);
        assertEquals("duplicate should be ignored", 1, mgr.size());
    }
}
