package crossspire.combat;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure HP/block sampling for stage-host monster turn authority (P6 / ARCHITECTURE §10).
 * Engine patches call these helpers; unit tests cover delta math without STS.
 */
public final class MonsterTurnCapture {

    private MonsterTurnCapture() {}

    public static final class Snapshot {
        public final int hp;
        public final int block;
        public final String targetId;

        public Snapshot(int hp, int block, String targetId) {
            this.hp = hp;
            this.block = block;
            this.targetId = targetId == null ? "self" : targetId;
        }
    }

    public static final class Delta {
        public final int damageToPlayer;
        public final int blockLost;
        public final String targetId;

        public Delta(int damageToPlayer, int blockLost, String targetId) {
            this.damageToPlayer = Math.max(0, damageToPlayer);
            this.blockLost = Math.max(0, blockLost);
            this.targetId = targetId == null ? "self" : targetId;
        }

        public boolean hasEffects() {
            return damageToPlayer > 0 || blockLost > 0;
        }
    }

    public static Delta diff(Snapshot before, Snapshot after) {
        if (before == null || after == null) {
            return new Delta(0, 0, "self");
        }
        int dmg = before.hp - after.hp;
        int blk = before.block - after.block;
        if (dmg < 0) dmg = 0;
        if (blk < 0) blk = 0;
        return new Delta(dmg, blk, after.targetId);
    }

    public static Protocol.EffectDescription[] toEffects(Delta d) {
        if (d == null || !d.hasEffects()) {
            return new Protocol.EffectDescription[0];
        }
        List<Protocol.EffectDescription> list = new ArrayList<Protocol.EffectDescription>();
        if (d.damageToPlayer > 0) {
            Protocol.EffectDescription e = new Protocol.EffectDescription();
            e.kind = "damage";
            e.target = d.targetId;
            e.amount = d.damageToPlayer;
            list.add(e);
        }
        if (d.blockLost > 0) {
            // Represent block removal as lose_hp-style side channel is wrong;
            // use gain_block negative amount not in schema — emit damage-only for HP.
            // Block loss on player is often folded into damage; if only block changed, skip.
        }
        return list.toArray(new Protocol.EffectDescription[0]);
    }

    /** Build combat_result effects for player HP loss (monster attacks). */
    public static Protocol.EffectDescription[] playerHpLossEffects(int amount) {
        if (amount <= 0) return new Protocol.EffectDescription[0];
        Protocol.EffectDescription e = new Protocol.EffectDescription();
        e.kind = "damage";
        e.target = "self";
        e.amount = amount;
        return new Protocol.EffectDescription[] { e };
    }
}
