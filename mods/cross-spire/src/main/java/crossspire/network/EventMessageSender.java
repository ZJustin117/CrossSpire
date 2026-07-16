package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;

public final class EventMessageSender {

    private static final Gson GSON = new Gson();

    private EventMessageSender() {}

    /**
     * Builds event_interface message sent from stage host to all players.
     * @param eventId    event internal name (class simple name)
     * @param description event body text
     * @param optionTexts option labels (index = optionIndex)
     * @param disabled    true if option is greyed out (same index)
     */
    public static String buildEventInterface(
            String eventId, String eventClass, String description,
            String[] optionTexts, boolean[] disabled) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "event_interface");
        obj.addProperty("source", CrossSpireMod.playerId);
        obj.addProperty("event_id", eventId);
        obj.addProperty("event_class", eventClass);
        obj.addProperty("description", description);

        JsonArray options = new JsonArray();
        int count = Math.min(optionTexts.length, disabled.length);
        for (int i = 0; i < count; i++) {
            JsonObject opt = new JsonObject();
            opt.addProperty("index", i);
            opt.addProperty("text", optionTexts[i]);
            opt.addProperty("disabled", disabled[i]);
            options.add(opt);
        }
        obj.add("options", options);
        return GSON.toJson(obj);
    }

    public static String buildEventSelect(String source, int optionIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "event_select");
        obj.addProperty("source", source);
        obj.addProperty("option_index", optionIndex);
        return GSON.toJson(obj);
    }

    public static String buildEventResult(String eventId, int gold, int hpDelta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "event_result");
        obj.addProperty("source", CrossSpireMod.playerId);
        obj.addProperty("event_id", eventId);
        obj.addProperty("gold", gold);
        obj.addProperty("hp_delta", hpDelta);
        return GSON.toJson(obj);
    }
}
