package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;

/**
 * Builds stage host election messages matching spec US-4b / ARCH §6.
 * All messages are plain JSON (non-StandardPacket), routed through the room host.
 */
public final class StageVoteSender {

    private static final Gson GSON = new Gson();

    private StageVoteSender() {}

    public static String buildStageVote(String source, String candidate) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "stage_vote");
        obj.addProperty("source", source);
        obj.addProperty("candidate", candidate);
        return GSON.toJson(obj);
    }

    public static String buildStageVotes(String source, String votesJson) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "stage_votes");
        obj.addProperty("source", source);
        obj.add("votes", GSON.fromJson(votesJson, JsonObject.class));
        return GSON.toJson(obj);
    }

    public static String buildStageHostResult(String source, String hostId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "stage_host_result");
        obj.addProperty("source", source);
        obj.addProperty("host_id", hostId);
        return GSON.toJson(obj);
    }
}
