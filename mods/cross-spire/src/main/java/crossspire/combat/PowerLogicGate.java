package crossspire.combat;

/**
 * Pure ownership policy for power callbacks (testable without AbstractPower).
 * Non-logic-owners must not run passive power logic.
 */
public final class PowerLogicGate {

    private final String logicOwnerId;
    private boolean blockedByOwnership;

    public PowerLogicGate(String logicOwnerId) {
        this.logicOwnerId = logicOwnerId;
    }

    public String getLogicOwnerId() {
        return logicOwnerId;
    }

    public boolean isLocalLogicOwner() {
        return LocalOwnerGate.isLocalOwner(logicOwnerId);
    }

    /**
     * @return true if logic callbacks may run; false if blocked (non-owner).
     */
    public boolean allowLogic() {
        if (isLocalLogicOwner()) return true;
        blockedByOwnership = true;
        return false;
    }

    public boolean wasBlockedByOwnership() {
        return blockedByOwnership;
    }

    public void resetBlockedFlag() {
        blockedByOwnership = false;
    }
}
