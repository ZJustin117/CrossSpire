package crossspire.combat;

/**
 * Room-host combat turn state machine (P6 / ARCHITECTURE §9–10).
 * Pure transition rules; network broadcast stays on {@link CombatPhaseCoordinator}.
 */
public final class CombatTurnOrchestrator {

    private CombatTurnOrchestrator() {}

    public static void reset() {
        // Stateless rules; coordinator holds current phase.
    }

    public static String nextAfterEndTurnConsensus() {
        return CombatPhase.PRE_MONSTER_TURN;
    }

    public static String nextAfterPreMonster() {
        return CombatPhase.MONSTER_TURN;
    }

    public static String nextAfterMonsterTurnComplete() {
        return CombatPhase.POST_MONSTER_TURN;
    }

    public static String nextAfterPostMonster() {
        return CombatPhase.PLAYER_TURN;
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (!CombatPhase.isValid(from) || !CombatPhase.isValid(to)) return false;
        if (from.equals(to)) return true;

        if (CombatPhase.PLAYER_TURN.equals(from)) {
            return CombatPhase.RESOLVING_QUEUE.equals(to)
                || CombatPhase.QUEUE_EMPTY.equals(to)
                || CombatPhase.PRE_MONSTER_TURN.equals(to);
        }
        if (CombatPhase.RESOLVING_QUEUE.equals(from)) {
            return CombatPhase.QUEUE_EMPTY.equals(to);
        }
        if (CombatPhase.QUEUE_EMPTY.equals(from)) {
            return CombatPhase.PRE_MONSTER_TURN.equals(to)
                || CombatPhase.RESOLVING_QUEUE.equals(to)
                || CombatPhase.PLAYER_TURN.equals(to);
        }
        if (CombatPhase.PRE_MONSTER_TURN.equals(from)) {
            return CombatPhase.MONSTER_TURN.equals(to);
        }
        if (CombatPhase.MONSTER_TURN.equals(from)) {
            return CombatPhase.POST_MONSTER_TURN.equals(to);
        }
        if (CombatPhase.POST_MONSTER_TURN.equals(from)) {
            return CombatPhase.PLAYER_TURN.equals(to);
        }
        return false;
    }

    /**
     * Apply transition locally if legal from {@link CombatPhaseCoordinator#getCurrentPhase()}.
     * Does not send network packets.
     */
    public static boolean tryApplyTransition(String to, String transactionId) {
        String from = CombatPhaseCoordinator.getCurrentPhase();
        if (!canTransition(from, to)) return false;
        CombatPhaseCoordinator.applyLocal(to, transactionId);
        return true;
    }

    /** Host may broadcast only if transition from current phase is legal. */
    public static boolean canBroadcast(String to) {
        return canTransition(CombatPhaseCoordinator.getCurrentPhase(), to);
    }

    public static boolean allowsQueueSubmit(String phase) {
        if (phase == null) return false;
        return CombatPhase.PLAYER_TURN.equals(phase)
            || CombatPhase.QUEUE_EMPTY.equals(phase)
            || CombatPhase.RESOLVING_QUEUE.equals(phase);
    }

    public static boolean allowsQueueSubmitCurrent() {
        return allowsQueueSubmit(CombatPhaseCoordinator.getCurrentPhase());
    }

    /** Stage host runs monster AI / HP delta capture only in monster_turn. */
    public static boolean shouldStageHostRunMonsterAi() {
        return CombatPhase.MONSTER_TURN.equals(CombatPhaseCoordinator.getCurrentPhase());
    }

    /** Non-stage-host clients must not locally execute authoritative monster AI. */
    public static boolean shouldSuppressMonsterAi(boolean connected, boolean isStageHost) {
        return connected && !isStageHost;
    }

    /** Connected games execute monster AI only on the stage host in monster_turn. */
    public static boolean shouldAllowMonsterAi(boolean connected, boolean isStageHost, String phase) {
        return !connected || (isStageHost && CombatPhase.MONSTER_TURN.equals(phase));
    }
}
