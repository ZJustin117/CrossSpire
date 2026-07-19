package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.ui.QueueDisplay;
import java.util.UUID;

/**
 * Tracks and broadcasts room-host combat phase alignment.
 * Safe to call from unit tests when not connected (no network).
 */
public final class CombatPhaseCoordinator {

    private static volatile String currentPhase = CombatPhase.PLAYER_TURN;
    private static volatile String lastTransactionId = "";

    private CombatPhaseCoordinator() {}

    public static void reset() {
        currentPhase = CombatPhase.PLAYER_TURN;
        lastTransactionId = "";
    }

    public static String getCurrentPhase() {
        return currentPhase;
    }

    public static String getLastTransactionId() {
        return lastTransactionId;
    }

    /**
     * Apply a phase locally (from host broadcast or local host decision).
     * Does not send network messages.
     */
    public static void applyLocal(String phase, String transactionId) {
        if (!CombatPhase.isValid(phase)) {
            log("ignore invalid phase: " + phase);
            return;
        }
        currentPhase = phase;
        if (transactionId != null && !transactionId.isEmpty()) {
            lastTransactionId = transactionId;
        }
        try {
            if (CombatPhase.QUEUE_EMPTY.equals(phase)) {
                QueueDisplay.onQueueEmpty();
            } else if (CombatPhase.RESOLVING_QUEUE.equals(phase)
                || CombatPhase.PLAYER_TURN.equals(phase)
                || CombatPhase.PRE_MONSTER_TURN.equals(phase)) {
                if (!CombatPhase.QUEUE_EMPTY.equals(phase)) {
                    QueueDisplay.resetEndTurn();
                }
            }
        } catch (Throwable t) {
            // QueueDisplay / BaseMod may be unavailable in pure unit tests
        }
        log("phase=" + phase + " tx=" + (lastTransactionId.isEmpty() ? "-" : lastTransactionId));
    }

    /**
     * Room host: set phase and broadcast combat_phase to all peers.
     * No-op network when not connected; still updates local state when host.
     */
    public static void broadcast(String phase) {
        if (!CombatPhase.isValid(phase)) return;
        String tx = UUID.randomUUID().toString().substring(0, 8);
        applyLocal(phase, tx);

        if (!CrossSpireMod.isConnected()) return;
        if (!CrossSpireMod.isRoomHost()) return;

        Protocol.CombatPhaseMessage msg = new Protocol.CombatPhaseMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.phase = phase;
        msg.transactionId = tx;
        CrossSpireMod.send(Protocol.GSON.toJson(msg));
        log("broadcast " + phase + " tx=" + tx);
    }

    /** Build JSON for tests without mutating global state. */
    public static String buildMessageJson(String source, int seq, String phase, String transactionId) {
        Protocol.CombatPhaseMessage msg = new Protocol.CombatPhaseMessage();
        msg.source = source;
        msg.seq = seq;
        msg.phase = phase;
        msg.transactionId = transactionId;
        return Protocol.GSON.toJson(msg);
    }

    public static final String OPERATION = PacketOperation.COMBAT_PHASE;

    private static void log(String msg) {
        System.out.println("[CrossSpire] CombatPhaseCoordinator " + msg);
    }
}
