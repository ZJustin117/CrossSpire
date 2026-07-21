package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;

public final class RoomPinSender {

    private static final Gson GSON = new Gson();

    private RoomPinSender() {}

    public static String buildRoomPin(String source, String partyId, int roomIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_pin");
        obj.addProperty("source", source);
        obj.addProperty("party_id", partyId);
        obj.addProperty("room", roomIndex);
        return GSON.toJson(obj);
    }

    /** @deprecated use party-scoped overload */
    public static String buildRoomPin(String source, int roomIndex) {
        return buildRoomPin(source, "P0", roomIndex);
    }

    public static String buildRoomPins(String source, String partyId, Map<String, String> pins) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_pins");
        obj.addProperty("source", source);
        obj.addProperty("party_id", partyId);
        obj.add("pins", GSON.toJsonTree(pins));
        return GSON.toJson(obj);
    }

    public static String buildRoomPins(String source, String pinsJson) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_pins");
        obj.addProperty("source", source);
        obj.addProperty("party_id", "P0");
        obj.add("pins", GSON.fromJson(pinsJson, JsonObject.class));
        return GSON.toJson(obj);
    }

    public static String buildRoomConsensus(String source, String partyId,
                                            String mapInstanceId, String nodeId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_consensus");
        obj.addProperty("source", source);
        obj.addProperty("party_id", partyId);
        obj.addProperty("map_instance_id", mapInstanceId);
        obj.addProperty("node_id", nodeId);
        return GSON.toJson(obj);
    }

    public static String buildRoomConsensus(String source, int roomIndex) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "room_consensus");
        obj.addProperty("source", source);
        obj.addProperty("party_id", "P0");
        obj.addProperty("room", roomIndex);
        return GSON.toJson(obj);
    }
}
