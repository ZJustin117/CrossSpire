package crossspire.combat;

/**
 * Room-host authoritative combat phase alignment (ARCHITECTURE §9 / FR-2.8).
 * Clients advance local gates; buffs fire spontaneously only for local logic owners.
 */
public final class CombatPhase {

    public static final String PLAYER_TURN = "player_turn";
    public static final String RESOLVING_QUEUE = "resolving_queue";
    public static final String QUEUE_EMPTY = "queue_empty";
    public static final String PRE_MONSTER_TURN = "pre_monster_turn";
    public static final String MONSTER_TURN = "monster_turn";
    public static final String POST_MONSTER_TURN = "post_monster_turn";

    private CombatPhase() {}

    public static boolean isValid(String phase) {
        if (phase == null) return false;
        return PLAYER_TURN.equals(phase)
            || RESOLVING_QUEUE.equals(phase)
            || QUEUE_EMPTY.equals(phase)
            || PRE_MONSTER_TURN.equals(phase)
            || MONSTER_TURN.equals(phase)
            || POST_MONSTER_TURN.equals(phase);
    }

    /** Whether EndTurnButton may be enabled for this phase. */
    public static boolean allowsEndTurn(String phase) {
        return QUEUE_EMPTY.equals(phase) || PLAYER_TURN.equals(phase);
    }

    /** Whether queue_submit / card play is allowed (player side of the turn). */
    public static boolean allowsQueueSubmit(String phase) {
        return CombatTurnOrchestrator.allowsQueueSubmit(phase);
    }
}
