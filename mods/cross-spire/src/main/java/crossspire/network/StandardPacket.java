package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class StandardPacket {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @SerializedName("packet_id")
    public String packetId;
    public String source;
    public int seq;
    public long timestamp;
    @SerializedName("ref_id")
    public String refId;
    @SerializedName("owner_id")
    public String ownerId;
    @SerializedName("resource_hash")
    public String resourceHash;
    public String operation;
    public JsonObject payload;

    public static String toJson(StandardPacket pkt) {
        return GSON.toJson(pkt);
    }

    public static StandardPacket fromJson(String raw) {
        return GSON.fromJson(raw, StandardPacket.class);
    }
}
