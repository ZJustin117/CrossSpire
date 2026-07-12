package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public final class Protocol {

    public static final Gson GSON = new Gson();

    public static class GameMessage {
        public String type;
        public String subtype;
        public String source;
        public int seq;
        public String target;
    }

    // -- distributed broadcast queue --

    public static class QueuePacket extends GameMessage {
        public QueuePacket() { type = "queue_packet"; }
        @SerializedName("packet_id") public String packetId;
        @SerializedName("sender_id") public String senderId;
        @SerializedName("owner_id") public String ownerId;
        public long timestamp;
        @SerializedName("card_id") public String cardId;
        @SerializedName("resource_hash") public String resourceHash;
    }

    public static class EffectDescription {
        public String kind;
        public String target;
        public int amount;
        @SerializedName("card_id") public String cardId;
        @SerializedName("relic_id") public String relicId;
        @SerializedName("potion_id") public String potionId;
        @SerializedName("power_id") public String powerId;
        @SerializedName("damage_type") public String damageType;
    }

    public static class OperationStep {
        public String step;
        @SerializedName("card_id") public String cardId;
        public String source;
        public String target;
        public int amount;
    }

    public static class QueueComplete extends GameMessage {
        public QueueComplete() { type = "queue_complete"; }
        @SerializedName("packet_id") public String packetId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    // -- state sync --

    public static class RemotePlayerState {
        public int hp;
        @SerializedName("max_hp") public int maxHp;
        public int block;
        public int energy;
        public String[] relics;
        public String[] potions;
    }

    public static class PlayerStateMessage extends GameMessage {
        public PlayerStateMessage() { type = "player_state"; }
        public RemotePlayerState player;
    }

    public static class StageSync extends GameMessage {
        public StageSync() { type = "stage_sync"; }
        public String character;
        public String seed;
        public int act;
    }

    // -- join / room messages --

    public static class HelloMessage extends GameMessage {
        public HelloMessage() { type = "hello"; }
        public String ip;
        public int port;
    }

    public static class RoomInfoMessage extends GameMessage {
        public RoomInfoMessage() { type = "room_info"; }
        public String name;
        @SerializedName("stage_host") public String stageHost;
        @SerializedName("members") public MemberInfo[] members;
    }

    // -- lobby ready --

    public static class PlayerReady extends GameMessage {
        public PlayerReady() { type = "player_ready"; }
        public String character;
    }

    public static class MemberInfo {
        public String id;
        public String ip;
        public int port;
    }

    // -- reference invoke --

    public static class InvokeMessage extends GameMessage {
        public InvokeMessage() { type = "invoke"; }
        @SerializedName("ref_id") public String refId;
        public String trigger;
        public String args;
    }

    public static class InvokeResultMessage extends GameMessage {
        public InvokeResultMessage() { type = "invoke_result"; }
        @SerializedName("ref_id") public String refId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    // -- keep legacy types for backward compat with RelayClient --

    public static class QueueSubmit extends GameMessage {
        public QueueSubmit() { type = "queue_submit"; }
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        @SerializedName("energy_cost") public int energyCost;
    }

    public static class QueueEntry {
        @SerializedName("entry_id") public String entryId;
        public String source;
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        public String target;
        public String status;
    }

    public static class QueueUpdate extends GameMessage {
        public QueueUpdate() { type = "queue_update"; }
        public QueueEntry[] entries;
    }

    public static class InvokeCard extends GameMessage {
        public InvokeCard() { type = "invoke_card"; }
        @SerializedName("request_id") public String requestId;
        @SerializedName("card_id") public String cardId;
        public boolean upgraded;
        @SerializedName("game_target") public String gameTarget;
    }

    public static class InvokeResult extends GameMessage {
        public InvokeResult() { type = "invoke_result"; }
        @SerializedName("request_id") public String requestId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    public static class RemotePlayerSync extends GameMessage {
        public RemotePlayerSync() { type = "state_sync"; subtype = "remote_player"; }
        public RemotePlayerState player;
    }

    private Protocol() {}
}
