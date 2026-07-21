package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.network.PacketOperation;
import crossspire.network.Protocol;
import crossspire.party.PartyManager;
import crossspire.party.PartyCoordinator;
import crossspire.ui.QueueDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks combat phase alignment independently for each gameplay party. */
public final class CombatPhaseCoordinator {

    private static final class PhaseState {
        private String currentPhase = CombatPhase.PLAYER_TURN;
        private String lastTransactionId = "";
    }

    private static final Map<String, PhaseState> states = new HashMap<String, PhaseState>();

    private CombatPhaseCoordinator() {}

    public static void reset() {
        synchronized (states) {
            states.clear();
        }
    }

    public static String normalizePartyId(String partyId) {
        return partyId == null || partyId.isEmpty() ? PartyManager.DEFAULT_PARTY_ID : partyId;
    }

    public static String getCurrentPhase() {
        return getCurrentPhase(PartyManager.DEFAULT_PARTY_ID);
    }

    public static String getCurrentPhase(String partyId) {
        synchronized (states) {
            return stateFor(partyId).currentPhase;
        }
    }

    public static String getLastTransactionId() {
        return getLastTransactionId(PartyManager.DEFAULT_PARTY_ID);
    }

    public static String getLastTransactionId(String partyId) {
        synchronized (states) {
            return stateFor(partyId).lastTransactionId;
        }
    }

    public static void applyLocal(String phase, String transactionId) {
        applyLocal(PartyManager.DEFAULT_PARTY_ID, phase, transactionId);
    }

    /** Applies a local phase without sending a network message. */
    public static void applyLocal(String partyId, String phase, String transactionId) {
        if (!CombatPhase.isValid(phase)) {
            log("ignore invalid phase: " + phase);
            return;
        }
        String effectivePartyId = normalizePartyId(partyId);
        PhaseState state;
        synchronized (states) {
            state = stateFor(effectivePartyId);
            state.currentPhase = phase;
            if (transactionId != null && !transactionId.isEmpty()) {
                state.lastTransactionId = transactionId;
            }
        }
        if (controlsLocalParty(effectivePartyId)) applyUiGate(phase);
        log("party=" + effectivePartyId + " phase=" + phase + " tx="
            + (state.lastTransactionId.isEmpty() ? "-" : state.lastTransactionId));
    }

    public static boolean tryApplyRemote(String source, String roomHostId,
                                         String phase, String transactionId) {
        return tryApplyRemote(PartyManager.DEFAULT_PARTY_ID, source, roomHostId, phase, transactionId);
    }

    /** Applies a leader-authorized phase only once when it advances that party's state machine. */
    public static boolean tryApplyRemote(String partyId, String source, String leaderId,
                                         String phase, String transactionId) {
        if (source == null || source.isEmpty() || !source.equals(leaderId)) {
            log("reject remote phase from non-leader: " + source);
            return false;
        }
        String effectivePartyId = normalizePartyId(partyId);
        PhaseState state;
        synchronized (states) {
            state = stateFor(effectivePartyId);
        }
        if (transactionId == null || transactionId.isEmpty()
            || transactionId.equals(state.lastTransactionId)) {
            log("reject duplicate or missing remote phase transaction: " + transactionId);
            return false;
        }
        if (!CombatTurnOrchestrator.canTransition(state.currentPhase, phase)
            || state.currentPhase.equals(phase)) {
            log("reject illegal remote phase " + state.currentPhase + " -> " + phase);
            return false;
        }
        applyLocal(effectivePartyId, phase, transactionId);
        return true;
    }

    /** Existing P0 broadcast compatibility. */
    public static void broadcast(String phase) {
        broadcast(PartyManager.DEFAULT_PARTY_ID, phase);
    }

    public static void broadcast(String partyId, String phase) {
        if (!CombatPhase.isValid(phase)) return;
        String effectivePartyId = normalizePartyId(partyId);
        if (!PartyCoordinator.isLeader(CrossSpireMod.partyManager, effectivePartyId, CrossSpireMod.playerId)) {
            log("reject broadcast from non-leader party=" + effectivePartyId);
            return;
        }
        if (CrossSpireMod.isConnected()
            && !CombatTurnOrchestrator.canTransition(getCurrentPhase(effectivePartyId), phase)
            && !phase.equals(getCurrentPhase(effectivePartyId))) {
            log("reject illegal broadcast " + getCurrentPhase(effectivePartyId) + " -> " + phase);
            return;
        }
        String tx = UUID.randomUUID().toString().substring(0, 8);
        applyLocal(effectivePartyId, phase, tx);

        if (!CrossSpireMod.isConnected()) return;
        Protocol.CombatPhaseMessage msg = new Protocol.CombatPhaseMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.partyId = effectivePartyId;
        msg.phase = phase;
        msg.transactionId = tx;
        CrossSpireMod.sendToParty(effectivePartyId, Protocol.GSON.toJson(msg));
        log("broadcast party=" + effectivePartyId + " phase=" + phase + " tx=" + tx);
    }

    public static boolean broadcastTransition(String phase) {
        if (!CombatPhase.isValid(phase)
            || !CombatTurnOrchestrator.canTransition(getCurrentPhase(), phase)
                && !phase.equals(getCurrentPhase())) {
            return false;
        }
        broadcast(phase);
        return true;
    }

    public static String buildMessageJson(String source, int seq, String phase, String transactionId) {
        return buildMessageJson(source, seq, PartyManager.DEFAULT_PARTY_ID, phase, transactionId);
    }

    /** Build JSON for tests without mutating state. */
    public static String buildMessageJson(String source, int seq, String partyId,
                                          String phase, String transactionId) {
        Protocol.CombatPhaseMessage msg = new Protocol.CombatPhaseMessage();
        msg.source = source;
        msg.seq = seq;
        msg.partyId = normalizePartyId(partyId);
        msg.phase = phase;
        msg.transactionId = transactionId;
        return Protocol.GSON.toJson(msg);
    }

    public static final String OPERATION = PacketOperation.COMBAT_PHASE;

    private static PhaseState stateFor(String partyId) {
        String effectivePartyId = normalizePartyId(partyId);
        PhaseState state = states.get(effectivePartyId);
        if (state == null) {
            state = new PhaseState();
            states.put(effectivePartyId, state);
        }
        return state;
    }

    private static void applyUiGate(String phase) {
        try {
            if (CombatPhase.QUEUE_EMPTY.equals(phase) || CombatPhase.PLAYER_TURN.equals(phase)) {
                QueueDisplay.onQueueEmpty();
            } else {
                QueueDisplay.resetEndTurn();
            }
        } catch (Throwable ignored) {
            // QueueDisplay / BaseMod may be unavailable in pure unit tests.
        }
    }

    private static boolean controlsLocalParty(String partyId) {
        if (CrossSpireMod.partyManager == null || CrossSpireMod.playerId == null
            || CrossSpireMod.playerId.isEmpty()) return PartyManager.DEFAULT_PARTY_ID.equals(partyId);
        String localPartyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        return partyId.equals(localPartyId != null ? localPartyId : PartyManager.DEFAULT_PARTY_ID);
    }

    private static void log(String msg) {
        System.out.println("[CrossSpire] CombatPhaseCoordinator " + msg);
    }
}
