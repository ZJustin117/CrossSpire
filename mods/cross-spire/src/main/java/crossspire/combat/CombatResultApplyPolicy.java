package crossspire.combat;

/**
 * Pure combat_result admit / induce / hop decisions (testable without STS).
 * Used by MessageRouter (host local induce) and CombatResultReplayer.
 */
public final class CombatResultApplyPolicy {

    public static final int MAX_INDUCED_HOP = 3;

    private CombatResultApplyPolicy() {}

    /**
     * After sendToParty (never delivers to self): apply peer combat_result locally
     * when executor is not this client.
     */
    public static boolean shouldLocalInduceAfterBroadcast(String localPlayerId, String executorId) {
        if (executorId == null || executorId.isEmpty()) return false;
        if (localPlayerId == null || localPlayerId.isEmpty()) return false;
        return !executorId.equals(localPlayerId);
    }

    /**
     * REAL executor already ran the card; skip INDUCED on the same machine.
     */
    public static boolean shouldSkipAsOwnResult(String localPlayerId, String executorId) {
        if (executorId == null || executorId.isEmpty()) return false;
        return executorId.equals(localPlayerId);
    }

    public static boolean shouldDropInducedHop(int hopCount) {
        return hopCount >= MAX_INDUCED_HOP;
    }

    public static int advanceHopCount(int hopCount) {
        return hopCount + 1;
    }

    /** Prefer executor_id; fall back to source. */
    public static String resolveExecutorId(String executorIdField, String sourceField) {
        if (executorIdField != null && !executorIdField.isEmpty()) return executorIdField;
        return sourceField != null ? sourceField : "";
    }

    /**
     * LOCAL_OWNER_ONLY: fire registered triggers only when logic_owner is this client.
     * Empty logic owner never fires (display-only projection).
     */
    public static boolean shouldFireLocalOwnerLogic(String logicOwnerId, String localPlayerId) {
        if (logicOwnerId == null || logicOwnerId.isEmpty()) return false;
        if (localPlayerId == null || localPlayerId.isEmpty()) return false;
        return logicOwnerId.equals(localPlayerId);
    }
}
