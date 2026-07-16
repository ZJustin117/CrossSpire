package crossspire.combat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;

public final class EventCapture {

    private static JsonArray actions;
    private static String currentEventId;

    private EventCapture() {}

    public static void startTranscript(String eventId) {
        currentEventId = eventId;
        actions = new JsonArray();
    }

    public static void appendButtonEffect(int index) {
        if (actions == null) return;
        JsonObject step = new JsonObject();
        step.addProperty("type", "buttonEffect");
        step.addProperty("index", index);
        actions.add(step);
    }

    public static void appendCardSelect(String[] cardIds) {
        if (actions == null) return;
        JsonObject step = new JsonObject();
        step.addProperty("type", "cardSelect");
        JsonArray arr = new JsonArray();
        for (String cid : cardIds) arr.add(cid);
        step.add("cards", arr);
        actions.add(step);
    }

    public static void appendConfirm() {
        if (actions == null) return;
        JsonObject step = new JsonObject();
        step.addProperty("type", "confirm");
        actions.add(step);
    }

    public static String buildTranscript() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "event_transcript");
        msg.addProperty("source", CrossSpireMod.playerId);
        msg.addProperty("seq", CrossSpireMod.nextSeq());
        msg.addProperty("event_id", currentEventId != null ? currentEventId : "");
        msg.add("actions", actions != null ? actions : new JsonArray());
        return new Gson().toJson(msg);
    }
}
