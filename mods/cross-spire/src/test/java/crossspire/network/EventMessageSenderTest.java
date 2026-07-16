package crossspire.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventMessageSenderTest {

    @Test
    public void shouldBuildEventInterfaceWithNameDescriptionAndOptions() {
        String[] optionTexts = {"[Leave]", "Fight", "[Locked] Require 50 Gold"};
        boolean[] disabled = {false, false, true};

        String json = EventMessageSender.buildEventInterface(
            "Big Fish", "A large fish blocks your path.",
            optionTexts, disabled
        );

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("event_interface", obj.get("type").getAsString());
        assertEquals("Big Fish", obj.get("event_id").getAsString());
        assertEquals("A large fish blocks your path.", obj.get("description").getAsString());

        JsonArray options = obj.getAsJsonArray("options");
        assertEquals(3, options.size());

        JsonObject opt0 = options.get(0).getAsJsonObject();
        assertEquals("[Leave]", opt0.get("text").getAsString());
        assertFalse(opt0.get("disabled").getAsBoolean());

        JsonObject opt2 = options.get(2).getAsJsonObject();
        assertTrue(opt2.get("disabled").getAsBoolean());
    }

    @Test
    public void shouldBuildEventSelectMessage() {
        String json = EventMessageSender.buildEventSelect("alice", 1);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("event_select", obj.get("type").getAsString());
        assertEquals("alice", obj.get("source").getAsString());
        assertEquals(1, obj.get("option_index").getAsInt());
    }

    @Test
    public void shouldBuildEventResultWithEffects() {
        String json = EventMessageSender.buildEventResult("World of Goop", 15, 20);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("event_result", obj.get("type").getAsString());
        assertEquals("World of Goop", obj.get("event_id").getAsString());
        assertEquals(15, obj.get("gold").getAsInt());
        assertEquals(20, obj.get("hp_delta").getAsInt());
    }
}
