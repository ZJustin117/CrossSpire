package crossspire.network;

public final class PacketOperation {

    public static final String QUEUE_SUBMIT = "queue_submit";
    public static final String QUEUE_UPDATE = "queue_update";
    public static final String QUEUE_EMPTY = "queue_empty";
    public static final String INVOKE = "invoke";
    public static final String INVOKE_RESULT = "invoke_result";
    public static final String COMBAT_RESULT = "combat_result";
    public static final String COMBAT_PHASE = "combat_phase";
    public static final String MONSTER_INTENT = "monster_intent";
    public static final String PLAYER_STATE = "player_state";
    public static final String FULL_SNAPSHOT = "full_snapshot";
    public static final String EVENT_INTERFACE = "event_interface";
    public static final String EVENT_SELECT = "event_select";
    public static final String EVENT_RESULT = "event_result";
    public static final String EVENT_CHOICE_REQUEST = "event_choice_request";
    public static final String EVENT_CHOICE_APPROVED = "event_choice_approved";
    public static final String EVENT_CHOICE_REJECTED = "event_choice_rejected";
    public static final String EVENT_PLAYER_RESULT = "event_player_result";
    public static final String EVENT_VOTES = "event_votes";
    public static final String INTERACT_REQUEST = "interact_request";
    public static final String INTERACT_RESPONSE = "interact_response";
    public static final String RESOURCE_REGISTRY = "resource_registry";
    public static final String RESOURCE_REQUEST = "resource_request";
    public static final String RESOURCE_RESPONSE = "resource_response";
    public static final String ANIMATION_SYNC = "animation_sync";
    public static final String PARTY_SNAPSHOT = "party_snapshot";
    public static final String PARTY_LEAVE_REQUEST = "party_leave_request";
    public static final String PARTY_JOIN_REQUEST = "party_join_request";
    public static final String PARTY_JOIN_APPROVED = "party_join_approved";
    public static final String PARTY_JOIN_REJECTED = "party_join_rejected";
    public static final String PARTY_LEADER_CHANGED = "party_leader_changed";
    public static final String STAGE_TRANSITION_OPEN = "stage_transition_open";
    public static final String MAP_HOST_VOTE = "map_host_vote";
    public static final String MAP_HOST_RESULT = "map_host_result";
    public static final String MAP_REGISTER = "map_register";
    public static final String MAP_REGISTERED = "map_registered";
    public static final String MAP_JOIN_REQUEST = "map_join_request";
    public static final String MAP_JOINED = "map_joined";
    public static final String NODE_INSTANCE_HOST_VOTE = "node_instance_host_vote";
    public static final String NODE_INSTANCE_HOST_RESULT = "node_instance_host_result";
    public static final String STAGE_TRANSITION_COMPLETE = "stage_transition_complete";
    public static final String NODE_INSTANCE_ALLOCATE = "node_instance_allocate";
    public static final String NODE_GENERATION_COMMIT = "node_generation_commit";
    public static final String NODE_INSTANCE_OPENED = "node_instance_opened";

    private PacketOperation() {}
}
