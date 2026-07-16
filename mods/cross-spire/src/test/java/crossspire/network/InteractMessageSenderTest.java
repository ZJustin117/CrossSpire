package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractMessageSenderTest {

    @Test
    public void shouldBuildInteractRequest() {
        String[] options = {"Strike_R", "Defend_R", "Bash"};
        String json = InteractMessageSender.buildInteractRequest(
            "alice", "card_select", options, "Choose a card", 1, 1);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("interact_request", obj.get("type").getAsString());
        assertEquals("alice", obj.get("source").getAsString());
        assertEquals("card_select", obj.get("select_type").getAsString());
        assertEquals("Choose a card", obj.get("prompt").getAsString());
        assertEquals(1, obj.get("min_select").getAsInt());
        assertEquals(1, obj.get("max_select").getAsInt());
        assertEquals(3, obj.getAsJsonArray("options").size());
    }

    @Test
    public void shouldBuildInteractResponse() {
        String[] selected = {"Strike_R"};
        String json = InteractMessageSender.buildInteractResponse("bob", selected);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("interact_response", obj.get("type").getAsString());
        assertEquals("bob", obj.get("source").getAsString());
        JsonArray sel = obj.getAsJsonArray("selected");
        assertEquals(1, sel.size());
        assertEquals("Strike_R", sel.get(0).getAsString());
    }
}
