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
    public static final String INTERACT_REQUEST = "interact_request";
    public static final String INTERACT_RESPONSE = "interact_response";
    public static final String RESOURCE_REGISTRY = "resource_registry";
    public static final String RESOURCE_REQUEST = "resource_request";
    public static final String RESOURCE_RESPONSE = "resource_response";
    public static final String ANIMATION_SYNC = "animation_sync";

    private PacketOperation() {}
}
