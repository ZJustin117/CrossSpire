package crossspire.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoomPinSenderTest {

    @Test
    public void shouldBuildPartyScopedRoomPinMessage() {
        String json = RoomPinSender.buildRoomPin("alice", "P0", 1);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_pin", obj.get("type").getAsString());
        assertEquals("alice", obj.get("source").getAsString());
        assertEquals("P0", obj.get("party_id").getAsString());
        assertEquals(1, obj.get("room").getAsInt());
    }

    @Test
    public void shouldBuildRoomPinsMessage() {
        Map<String, String> pins = new LinkedHashMap<String, String>();
        pins.put("alice", "node1");
        pins.put("bob", "node1");
        String json = RoomPinSender.buildRoomPins("host", "P0", pins);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_pins", obj.get("type").getAsString());
        assertEquals("P0", obj.get("party_id").getAsString());
        assertNotNull(obj.get("pins"));
    }

    @Test
    public void shouldBuildRoomConsensusWithNodeId() {
        String json = RoomPinSender.buildRoomConsensus("host", "P0", "M1", "node1");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("room_consensus", obj.get("type").getAsString());
        assertEquals("P0", obj.get("party_id").getAsString());
        assertEquals("M1", obj.get("map_instance_id").getAsString());
        assertEquals("node1", obj.get("node_id").getAsString());
    }
}
