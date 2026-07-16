package crossspire.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoomPinSenderTest {

    @Test
    public void shouldBuildRoomPinMessage() {
        String json = RoomPinSender.buildRoomPin("alice", 1);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_pin", obj.get("type").getAsString());
        assertEquals("alice", obj.get("source").getAsString());
        assertEquals(1, obj.get("room").getAsInt());
    }

    @Test
    public void shouldBuildRoomPinsMessage() {
        String json = RoomPinSender.buildRoomPins("host", "{\"alice\":1,\"bob\":1}");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_pins", obj.get("type").getAsString());
        assertEquals("host", obj.get("source").getAsString());
        assertNotNull(obj.get("pins"));
    }

    @Test
    public void shouldBuildRoomConsensusMessage() {
        String json = RoomPinSender.buildRoomConsensus("host", 0);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_consensus", obj.get("type").getAsString());
        assertEquals("host", obj.get("source").getAsString());
        assertEquals(0, obj.get("room").getAsInt());
    }
}
