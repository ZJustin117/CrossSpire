package crossspire.network;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public final class Protocol {

    public static final Gson GSON = new Gson();

    // -- primitive types --

    public static class EffectDescription {
        public String kind;
        public String target;
        public int amount;
        @SerializedName("card_id") public String cardId;
        @SerializedName("relic_id") public String relicId;
        @SerializedName("potion_id") public String potionId;
        @SerializedName("power_id") public String powerId;
        @SerializedName("damage_type") public String damageType;
        /** Applier-first logic owner for apply_power / buffs. */
        @SerializedName("logic_owner_id") public String logicOwnerId;
        /** Owner that produced this effect during local-owner-only induced replay. */
        @SerializedName("origin_owner_id") public String originOwnerId;
        /** Induced side-effect hop count; drop when exceeding policy limit. */
        @SerializedName("hop_count") public int hopCount;
    }

    public static class OperationStep {
        public String step;
        @SerializedName("card_id") public String cardId;
        @SerializedName("power_id") public String powerId;
        public String source;
        public String target;
        public int amount;
        @SerializedName("vfx_kind") public String vfxKind;
        @SerializedName("card_type") public String cardType;
        @SerializedName("card_rarity") public String cardRarity;
        @SerializedName("card_target") public String cardTarget;
        @SerializedName("logic_owner_id") public String logicOwnerId;
    }

    // -- state sync payloads --

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

    // -- combat payloads --

    public static class CombatResultPayload {
        @SerializedName("monster_id") public String monsterId;
        @SerializedName("card_id") public String cardId;
        /** Original REAL executor; must not be rewritten to room host. */
        @SerializedName("executor_id") public String executorId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    public static class MonsterIntentEntry {
        @SerializedName("monster_id") public String monsterId;
        public String intent;
        public int damage;
        public int hits;
        @SerializedName("target_id") public String targetId;
    }

    public static class MonsterIntentPayload {
        @SerializedName("monster_id") public String monsterId;
        public String intent;
        public int damage;
        public int hits;
        @SerializedName("target_id") public String targetId;
        public MonsterIntentEntry[] intents;
    }

    // -- event payloads --

    public static class EventResultPayload {
        @SerializedName("event_id") public String eventId;
        public EffectDescription[] effects;
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

    public static class QueueUpdatePayload {
        public QueueEntry[] entries;
    }

    public static class QueueSubmitPayload {
        @SerializedName("card_id") public String cardId;
        public String target;
    }

    // -- legacy compat classes (keep until all call sites migrated) --

    // OBSOLETED: use CombatResultPayload instead
    public static class CombatResultMessage extends GameMessage {
        public CombatResultMessage() { type = "combat_result"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("monster_id") public String monsterId;
        @SerializedName("turn_transaction_id") public String turnTransactionId;
        @SerializedName("card_id") public String cardId;
        @SerializedName("executor_id") public String executorId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    public static class QueueComplete extends GameMessage {
        public QueueComplete() { type = "queue_complete"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("packet_id") public String packetId;
        @SerializedName("executor_id") public String executorId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

    public static class MonsterIntentMessage extends GameMessage {
        public MonsterIntentMessage() { type = "monster_intent"; }
        @SerializedName("monster_id") public String monsterId;
        public String intent;
        public int damage;
        public int hits;
        @SerializedName("target_id") public String targetId;
        public MonsterIntentEntry[] intents;
    }

    public static class EventResultMessage extends GameMessage {
        public EventResultMessage() { type = "event_result"; }
        @SerializedName("event_id") public String eventId;
        public EffectDescription[] effects;
    }

    public static class GameMessage {
        public String type;
        public String subtype;
        public String source;
        public int seq;
        public String target;
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

    public static class PlayerReady extends GameMessage {
        public PlayerReady() { type = "player_ready"; }
        public String character;
    }

    /** Coordinated multi-client run start (T7.7a). Control message, not StandardPacket. */
    public static class PartyRunStart extends GameMessage {
        public PartyRunStart() { type = "party_run_start"; }
        @SerializedName("party_id") public String partyId;
        public String seed;
        public int act;
        @SerializedName("leader_id") public String leaderId;
        public PartyRunMember[] members;
    }

    public static class PartyRunMember {
        @SerializedName("player_id") public String playerId;
        public String character;
    }

    public static class PartyRunStartRequest extends GameMessage {
        public PartyRunStartRequest() { type = "party_run_start_request"; }
        @SerializedName("party_id") public String partyId;
        public String seed;
        public String character;
    }

    public static class RewardPhaseEnter extends GameMessage {
        public RewardPhaseEnter() { type = "reward_phase_enter"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("transaction_id") public String transactionId;
    }

    public static class RewardOffer extends GameMessage {
        public RewardOffer() { type = "reward_offer"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("card_ids") public String[] cardIds;
        @SerializedName("relic_ids") public String[] relicIds;
        @SerializedName("potion_ids") public String[] potionIds;
        @SerializedName("gold_min") public int goldMin;
        @SerializedName("gold_max") public int goldMax;
    }

    public static class RewardPick extends GameMessage {
        public RewardPick() { type = "reward_pick"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("card_id") public String cardId;
        @SerializedName("relic_id") public String relicId;
        @SerializedName("potion_id") public String potionId;
        public boolean skipped;
    }

    public static class RewardPlayerResult extends GameMessage {
        public RewardPlayerResult() { type = "reward_player_result"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("player_id") public String playerId;
        public EffectDescription[] effects;
    }

    public static class RewardDone extends GameMessage {
        public RewardDone() { type = "reward_done"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
    }

    public static class RewardPhaseComplete extends GameMessage {
        public RewardPhaseComplete() { type = "reward_phase_complete"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
    }

    /** RoomInstanceHost unlocks map navigation for the party (T9). */
    public static class RoomExitUnlocked extends GameMessage {
        public RoomExitUnlocked() { type = "room_exit_unlocked"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("room_instance_id") public String roomInstanceId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
        public String reason;
    }

    public static class RoomExitLocked extends GameMessage {
        public RoomExitLocked() { type = "room_exit_locked"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("room_instance_id") public String roomInstanceId;
        @SerializedName("node_instance_id") public String nodeInstanceId;
    }

    public static class MemberInfo {
        public String id;
        public String ip;
        public int port;
    }

    public static class InvokeMessage extends GameMessage {
        public InvokeMessage() { type = "invoke"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("ref_id") public String refId;
        public String trigger;
        public String args;
    }

    public static class InvokeResultMessage extends GameMessage {
        public InvokeResultMessage() { type = "invoke_result"; }
        @SerializedName("party_id") public String partyId;
        @SerializedName("ref_id") public String refId;
        public EffectDescription[] effects;
        @SerializedName("operation_sequence") public OperationStep[] operationSequence;
    }

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

    public static class PlayerEndTurnMessage extends GameMessage {
        public PlayerEndTurnMessage() { type = "player_end_turn"; }
        @SerializedName("party_id") public String partyId;
    }

    public static class QueueSubmitMessage extends GameMessage {
        public QueueSubmitMessage() { type = "queue_submit"; }
        @SerializedName("party_id") public String partyId;
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
        @SerializedName("party_id") public String partyId;
        public QueueEntry[] entries;
    }

    public static class QueueEmptyMessage extends GameMessage {
        public QueueEmptyMessage() { type = "queue_empty"; }
        @SerializedName("party_id") public String partyId;
    }

    /** Room host → all: explicit combat phase alignment (T5.4 / FR-2.8). */
    public static class CombatPhaseMessage extends GameMessage {
        public CombatPhaseMessage() { type = "combat_phase"; }
        @SerializedName("party_id") public String partyId;
        public String phase;
        @SerializedName("transaction_id") public String transactionId;
    }

    public static class PartyInfo {
        @SerializedName("party_id") public String partyId;
        @SerializedName("leader_id") public String leaderId;
        @SerializedName("member_ids") public String[] memberIds;
        @SerializedName("phase_status") public String phaseStatus;
        @SerializedName("act_id") public String actId;
        @SerializedName("map_instance_id") public String mapInstanceId;
        @SerializedName("map_position") public String mapPosition;
        @SerializedName("node_instance_host_id") public String nodeInstanceHostId;
        @SerializedName("active_node_instance_id") public String activeNodeInstanceId;
        @SerializedName("party_revision") public int partyRevision;
    }

    public static class PartySnapshotPayload {
        public PartyInfo[] parties;
    }

    public static class PartyLeaveRequestPayload {
        @SerializedName("party_id") public String partyId;
    }

    public static class PartyJoinRequestPayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("request_id") public String requestId;
    }

    public static class PartyJoinDecisionPayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("request_id") public String requestId;
        @SerializedName("player_id") public String playerId;
        public String reason;
    }

    public static class PartyLeaderChangedPayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("leader_id") public String leaderId;
    }

    // -- map and node-instance directory payloads --

    public static class MapNode {
        @SerializedName("node_id") public String nodeId;
        public int x;
        public int y;
        @SerializedName("room_type") public String roomType;
        public String icon;
        @SerializedName("burning_elite") public boolean burningElite;
        @SerializedName("outgoing_node_ids") public String[] outgoingNodeIds;
    }

    public static class MapDefinition {
        @SerializedName("map_instance_id") public String mapInstanceId;
        @SerializedName("act_id") public String actId;
        @SerializedName("map_revision") public int mapRevision;
        @SerializedName("generation_digest") public String generationDigest;
        @SerializedName("start_node_id") public String startNodeId;
        @SerializedName("boss_descriptor") public Object bossDescriptor;
        public MapNode[] nodes;
    }

    public static class NodeInstanceInfo {
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("map_instance_id") public String mapInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("node_id") public String nodeId;
        @SerializedName("room_type") public String roomType;
        @SerializedName("visit_id") public int visitId;
        @SerializedName("node_instance_host_id") public String nodeInstanceHostId;
        public String status;
        @SerializedName("generation_revision") public int generationRevision;
        @SerializedName("state_revision") public int stateRevision;
    }

    public static class MapHostVotePayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("candidate_id") public String candidateId;
        @SerializedName("map_host_id") public String mapHostId;
        @SerializedName("party_revision") public int partyRevision;
    }

    public static class NodeInstanceHostVotePayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("candidate_id") public String candidateId;
        @SerializedName("node_instance_host_id") public String nodeInstanceHostId;
        @SerializedName("party_revision") public int partyRevision;
    }

    public static class MapRegistrationPayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("map_host_id") public String mapHostId;
        @SerializedName("request_id") public String requestId;
        public MapDefinition map;
    }

    public static class MapRegisteredPayload {
        @SerializedName("party_id") public String partyId;
        @SerializedName("map_instance_id") public String mapInstanceId;
        @SerializedName("start_node_id") public String startNodeId;
        @SerializedName("map_revision") public int mapRevision;
        @SerializedName("party_revision") public int partyRevision;
        public MapDefinition map;
    }

    public static class NodeInstanceAllocatePayload {
        @SerializedName("node_instance") public NodeInstanceInfo nodeInstance;
        @SerializedName("request_id") public String requestId;
    }

    public static class NodeGenerationCommitPayload {
        @SerializedName("node_instance_id") public String nodeInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("map_instance_id") public String mapInstanceId;
        @SerializedName("node_id") public String nodeId;
        @SerializedName("generation_revision") public int generationRevision;
        @SerializedName("generation_result") public NodeGenerationResult generationResult;
    }

    public static class NodeGenerationResult {
        @SerializedName("room_type") public String roomType;
        @SerializedName("encounter") public String encounter;
        @SerializedName("node_id") public String nodeId;
        @SerializedName("event_interface") public EventInterfacePayload eventInterface;
        @SerializedName("shop_seed") public String shopSeed;
        @SerializedName("rest_options") public String[] restOptions;
        @SerializedName("treasure_tier") public String treasureTier;
    }

    public static class NodeInstanceOpenedPayload {
        @SerializedName("node_instance") public NodeInstanceInfo nodeInstance;
        @SerializedName("generation_result") public NodeGenerationResult generationResult;
    }

    // -- event approval (T7.4) --

    public static class EventOptionInfo {
        public int index;
        public String text;
        public boolean enabled;
        /** Legacy field from older event_interface builders. */
        public boolean disabled;
    }

    public static class EventInterfacePayload {
        @SerializedName("event_instance_id") public String eventInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("event_class") public String eventClass;
        @SerializedName("event_id") public String eventId;
        @SerializedName("resource_hash") public String resourceHash;
        public String name;
        public String description;
        public String mode;
        public EventOptionInfo[] options;
    }

    public static class EventChoiceRequestPayload {
        @SerializedName("event_instance_id") public String eventInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("request_id") public String requestId;
        @SerializedName("ui_step") public String uiStep;
        @SerializedName("option_index") public int optionIndex;
        @SerializedName("selected_cards") public String[] selectedCards;
        @SerializedName("selected_targets") public String[] selectedTargets;
        @SerializedName("resource_hash") public String resourceHash;
    }

    public static class EventChoiceDecisionPayload {
        @SerializedName("event_instance_id") public String eventInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("request_id") public String requestId;
        @SerializedName("ui_step") public String uiStep;
        @SerializedName("option_index") public int optionIndex;
        public String reason;
    }

    public static class SharedOutcome {
        public String type;
        @SerializedName("instance_id") public String instanceId;
        @SerializedName("option_index") public int optionIndex;
        /** Combat encounter key when type=event_room (e.g. Cultist). */
        public String encounter;
        /** Party members who joined this event-room path; only they force-enter combat. */
        @SerializedName("member_ids") public String[] memberIds;
    }

    public static class EventPlayerResultPayload {
        @SerializedName("event_instance_id") public String eventInstanceId;
        @SerializedName("party_id") public String partyId;
        @SerializedName("request_id") public String requestId;
        @SerializedName("player_id") public String playerId;
        public EffectDescription[] effects;
        @SerializedName("shared_outcome") public SharedOutcome sharedOutcome;
    }

    private Protocol() {}
}
