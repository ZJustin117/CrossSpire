package crossspire.party;

/**
 * Per-party map navigation lock for the active RoomInstance (T9).
 * Default locked after room open; only RoomInstanceHost unlock broadcasts open pin/continue.
 */
public final class RoomNavigationGate {

    private String partyId = "";
    private String roomInstanceId = "";
    private boolean exitUnlocked = true;
    private String unlockReason = "";

    /**
     * Enter or re-enter a room instance: navigation locked until host unlocks.
     * Empty roomInstanceId clears to a free-nav state (no active room).
     */
    public synchronized void onRoomOpened(String partyId, String roomInstanceId) {
        this.partyId = partyId != null ? partyId : "";
        this.roomInstanceId = roomInstanceId != null ? roomInstanceId : "";
        this.unlockReason = "";
        if (this.roomInstanceId.isEmpty()) {
            this.exitUnlocked = true;
        } else {
            this.exitUnlocked = false;
        }
    }

    public synchronized boolean unlock(String partyId, String roomInstanceId, String reason) {
        if (partyId != null && !partyId.isEmpty() && !this.partyId.isEmpty()
            && !partyId.equals(this.partyId)) {
            return false;
        }
        if (roomInstanceId != null && !roomInstanceId.isEmpty()
            && !this.roomInstanceId.isEmpty()
            && !roomInstanceId.equals(this.roomInstanceId)) {
            return false;
        }
        if (partyId != null && !partyId.isEmpty()) this.partyId = partyId;
        if (roomInstanceId != null && !roomInstanceId.isEmpty()) {
            this.roomInstanceId = roomInstanceId;
        }
        this.exitUnlocked = true;
        this.unlockReason = reason != null ? reason : "";
        return true;
    }

    public synchronized void lock(String partyId, String roomInstanceId) {
        if (partyId != null && !partyId.isEmpty()) this.partyId = partyId;
        if (roomInstanceId != null && !roomInstanceId.isEmpty()) {
            this.roomInstanceId = roomInstanceId;
        }
        this.exitUnlocked = false;
        this.unlockReason = "";
    }

    public synchronized void clear() {
        partyId = "";
        roomInstanceId = "";
        exitUnlocked = true;
        unlockReason = "";
    }

    public synchronized boolean isExitUnlocked() {
        return exitUnlocked;
    }

    public synchronized boolean blocksRoomPin() {
        return !exitUnlocked && roomInstanceId != null && !roomInstanceId.isEmpty();
    }

    public synchronized String getPartyId() {
        return partyId;
    }

    public synchronized String getRoomInstanceId() {
        return roomInstanceId;
    }

    public synchronized String getUnlockReason() {
        return unlockReason;
    }

    public synchronized String summary() {
        return "nav party=" + partyId
            + " room=" + roomInstanceId
            + " exitUnlocked=" + exitUnlocked
            + (unlockReason.isEmpty() ? "" : " reason=" + unlockReason);
    }
}
