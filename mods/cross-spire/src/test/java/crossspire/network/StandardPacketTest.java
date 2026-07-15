package crossspire.network;

import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StandardPacketTest {

    @Test
    public void shouldSerializeToJsonWithAllHeaderFields() {
        JsonObject payload = new JsonObject();
        payload.addProperty("card_id", "Strike_R");
        payload.addProperty("target", "monster_0");

        StandardPacket pkt = new StandardPacket();
        pkt.packetId = "alice-1";
        pkt.source = "alice";
        pkt.seq = 1;
        pkt.timestamp = 1700000000000L;
        pkt.refId = "card:Strike_R@alice";
        pkt.ownerId = "alice";
        pkt.resourceHash = "abc123def";
        pkt.operation = PacketOperation.QUEUE_SUBMIT;
        pkt.payload = payload;

        String json = StandardPacket.toJson(pkt);
        StandardPacket parsed = StandardPacket.fromJson(json);

        assertEquals("alice-1", parsed.packetId);
        assertEquals("alice", parsed.source);
        assertEquals(1, parsed.seq);
        assertEquals(1700000000000L, parsed.timestamp);
        assertEquals("card:Strike_R@alice", parsed.refId);
        assertEquals("alice", parsed.ownerId);
        assertEquals("abc123def", parsed.resourceHash);
        assertEquals(PacketOperation.QUEUE_SUBMIT, parsed.operation);
        assertNotNull(parsed.payload);
        assertEquals("Strike_R", parsed.payload.get("card_id").getAsString());
        assertEquals("monster_0", parsed.payload.get("target").getAsString());
    }

    @Test
    public void shouldOmitOptionalFieldsWhenNull() {
        StandardPacket pkt = new StandardPacket();
        pkt.packetId = "bob-2";
        pkt.source = "bob";
        pkt.seq = 2;
        pkt.timestamp = 1700000000001L;
        pkt.refId = "card:Defend_G@bob";
        pkt.ownerId = "bob";
        pkt.operation = PacketOperation.QUEUE_SUBMIT;
        pkt.payload = new JsonObject();

        String json = StandardPacket.toJson(pkt);
        StandardPacket parsed = StandardPacket.fromJson(json);

        assertNull(parsed.resourceHash);
    }
}
