package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public final class RoomPinSender {

    private static final Gson GSON = new Gson();

    private RoomPinSender() {}

    public static String buildRoomPin(String source, int roomIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_pin");
        obj.addProperty("source", source);
        obj.addProperty("room", roomIndex);
        return GSON.toJson(obj);
    }

    public static String buildRoomPins(String source, String pinsJson) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_pins");
        obj.addProperty("source", source);
        obj.add("pins", GSON.fromJson(pinsJson, JsonObject.class));
        return GSON.toJson(obj);
    }

    public static String buildRoomConsensus(String source, int roomIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_consensus");
        obj.addProperty("source", source);
        obj.addProperty("room", roomIndex);
        return GSON.toJson(obj);
    }
}
