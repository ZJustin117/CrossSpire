package crossspire.event;

import crossspire.network.Protocol;

/**
 * Pure rules for when an approved event option creates a shared event-room path,
 * and which STS encounter to open for that path (T7.5).
 */
public final class EventRoomOutcomePlanner {

    private EventRoomOutcomePlanner() {}

    public static boolean createsEventRoom(Protocol.EventInterfacePayload iface, int optionIndex) {
        if (iface == null || iface.options == null) return false;
        for (Protocol.EventOptionInfo opt : iface.options) {
            if (opt == null || opt.index != optionIndex) continue;
            String text = opt.text != null ? opt.text.toLowerCase() : "";
            if (text.contains("fight") || text.contains("combat") || text.contains("battle")
                || text.contains("attack") || text.contains("challenge")) {
                return true;
            }
            // BigFish option 1 is "Fight" in factory; also treat known indices for diagnostic.
            if ("BigFish".equals(iface.eventId) && optionIndex == 1) return true;
            return false;
        }
        return false;
    }

    public static boolean requiresLeaveParty(Protocol.EventInterfacePayload iface, int optionIndex) {
        if (iface == null || iface.options == null) return false;
        for (Protocol.EventOptionInfo opt : iface.options) {
            if (opt == null || opt.index != optionIndex) continue;
            String text = opt.text != null ? opt.text.toLowerCase() : "";
            return text.contains("leave") && text.contains("map");
        }
        return false;
    }

    /**
     * Deterministic encounter for an event-room combat path.
     * Defaults to Cultist when unknown so dual-client can still open a shared shell.
     */
    public static String resolveEncounter(Protocol.EventInterfacePayload iface, int optionIndex) {
        if (iface == null) return "Cultist";
        String eventId = iface.eventId != null ? iface.eventId : "";
        if ("BigFish".equals(eventId) && optionIndex == 1) return "Cultist";
        if (eventId.toLowerCase().contains("dead adventurer")) return "Cultist";
        if (eventId.toLowerCase().contains("masked bandits")) return "Looter";
        if (createsEventRoom(iface, optionIndex)) return "Cultist";
        return "Cultist";
    }

    public static boolean shouldLocalPlayerEnterEventRoom(Protocol.SharedOutcome outcome,
                                                          String localPlayerId) {
        if (outcome == null || localPlayerId == null || localPlayerId.isEmpty()) return false;
        if (!"event_room".equals(outcome.type)) return false;
        if (outcome.memberIds == null || outcome.memberIds.length == 0) {
            // Backward-compatible: no member list → chooser path may still enter via player result.
            return true;
        }
        for (String id : outcome.memberIds) {
            if (localPlayerId.equals(id)) return true;
        }
        return false;
    }
}
