package crossspire.combat;

import java.util.HashSet;
import java.util.Set;

/** Admits each stage-host monster-turn completion once for its active phase transaction. */
public final class MonsterTurnResultGate {

    private final Set<String> accepted = new HashSet<String>();

    public synchronized boolean admit(String currentPhase, String activeTransactionId,
                                      String monsterId, String source, String stageHostId,
                                      int seq, String turnTransactionId) {
        if (!CombatPhase.MONSTER_TURN.equals(currentPhase)
            || !"monster_turn".equals(monsterId)
            || source == null || !source.equals(stageHostId)
            || activeTransactionId == null || activeTransactionId.isEmpty()
            || !activeTransactionId.equals(turnTransactionId)) {
            return false;
        }
        return accepted.add(source + "|" + seq + "|" + turnTransactionId);
    }

    public synchronized void reset() {
        accepted.clear();
    }
}
