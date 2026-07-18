package crossspire.combat;

import crossspire.CrossSpireMod;
import crossspire.reference.Reference;

/**
 * Gates induced-replay passive execution to local logic owners only.
 * Non-owners must not re-run remote buffs via full hook replay.
 */
public final class LocalOwnerGate {

    private LocalOwnerGate() {}

    public static boolean isLocalOwner(String logicOwnerId) {
        if (logicOwnerId == null || logicOwnerId.isEmpty()) return false;
        String self = CrossSpireMod.playerId;
        return self != null && self.equals(logicOwnerId);
    }

    public static boolean isLocalOwner(Reference<?> ref) {
        return ref != null && isLocalOwner(ref.ownerId);
    }

    /** Prefer explicit logic_owner_id; fall back to ref owner for registry entries. */
    public static boolean mayFirePassive(String logicOwnerId, Reference<?> ref) {
        if (logicOwnerId != null && !logicOwnerId.isEmpty()) {
            return isLocalOwner(logicOwnerId);
        }
        return isLocalOwner(ref);
    }
}
