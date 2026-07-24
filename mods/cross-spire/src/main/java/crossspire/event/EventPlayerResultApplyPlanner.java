package crossspire.event;

import crossspire.network.Protocol;

/** Pure rules for who applies a personal event_player_result locally. */
public final class EventPlayerResultApplyPlanner {

    private EventPlayerResultApplyPlanner() {}

    public static boolean shouldApplyLocally(Protocol.EventPlayerResultPayload result, String localPlayerId) {
        if (result == null || empty(localPlayerId) || empty(result.playerId)) return false;
        return localPlayerId.equals(result.playerId);
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
