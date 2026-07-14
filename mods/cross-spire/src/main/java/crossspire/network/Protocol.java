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
        @SerializedName("power_id") public String powerId;
        public String source;
        public String target;
        public int amount;
        @SerializedName("vfx_kind") public String vfxKind;
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
        public int gold;
        @SerializedName("character_class") public String characterClass;
        public String[] powers;
        @SerializedName("power_amounts") public int[] powerAmounts;
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
        public MemberInfo[] peers;
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

    // -- monster / combat messages --

    public static class MonsterIntentMessage extends GameMessage {
        public MonsterIntentMessage() { type = "monster_intent"; }
        @SerializedName("monster_id") public String monsterId;
        public String intent;
        public int damage;
        public int hits;
        @SerializedName("target_id") public String targetId;
    }

    public static class CombatResultMessage extends GameMessage {
        public CombatResultMessage() { type = "combat_result"; }
        @SerializedName("monster_id") public String monsterId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    // -- event messages --

    public static class EventResultMessage extends GameMessage {
        public EventResultMessage() { type = "event_result"; }
        @SerializedName("event_id") public String eventId;
        public EffectDescription[] effects;
    }

    // -- reference management --

    public static class ReferenceRegisterMessage extends GameMessage {
        public ReferenceRegisterMessage() { type = "reference_register"; }
        @SerializedName("ref_id") public String refId;
        @SerializedName("resource_type") public String resourceType;
        @SerializedName("resource_id") public String resourceId;
        @SerializedName("resource_hash") public String resourceHash;
    }

    public static class ReferenceMigrateMessage extends GameMessage {
        public ReferenceMigrateMessage() { type = "reference_migrate"; }
        @SerializedName("ref_id") public String refId;
        @SerializedName("resource_type") public String resourceType;
        @SerializedName("resource_id") public String resourceId;
        @SerializedName("resource_hash") public String resourceHash;
    }

    // -- host election / snapshot / animation --

    public static class StageHostElectionMessage extends GameMessage {
        public StageHostElectionMessage() { type = "stage_host_election"; }
        @SerializedName("candidates") public String[] candidates;
    }

    public static class StageHostResultMessage extends GameMessage {
        public StageHostResultMessage() { type = "stage_host_result"; }
        @SerializedName("host_id") public String hostId;
    }

    public static class FullSnapshotMessage extends GameMessage {
        public FullSnapshotMessage() { type = "full_snapshot"; }
        @SerializedName("stage_sync") public StageSync stage;
        @SerializedName("players") public RemotePlayerState[] players;
        @SerializedName("pending_messages") public GameMessage[] pendingMessages;
    }

    public static class AnimationSyncMessage extends GameMessage {
        public AnimationSyncMessage() { type = "animation_sync"; }
        @SerializedName("player_id") public String playerId;
        @SerializedName("animation_name") public String animationName;
    }

    // -- turn control --

    public static class PlayerEndTurnMessage extends GameMessage {
        public PlayerEndTurnMessage() { type = "player_end_turn"; }
    }

    // -- host-centric queue messages --

    public static class QueueSubmitMessage extends GameMessage {
        public QueueSubmitMessage() { type = "queue_submit"; }
        @SerializedName("packet_id") public String packetId;
        @SerializedName("sender_id") public String senderId;
        @SerializedName("owner_id") public String ownerId;
        @SerializedName("card_id") public String cardId;
        @SerializedName("resource_hash") public String resourceHash;
        @SerializedName("game_target") public String gameTarget;
        public long timestamp;
    }

    public static class QueueUpdateMessage extends GameMessage {
        public QueueUpdateMessage() { type = "queue_update"; }
        public QueueEntry[] entries;
    }

    public static class QueueEmptyMessage extends GameMessage {
        public QueueEmptyMessage() { type = "queue_empty"; }
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
        @SerializedName("packet_id") public String packetId;
        @SerializedName("sender_id") public String senderId;
        @SerializedName("owner_id") public String ownerId;
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
