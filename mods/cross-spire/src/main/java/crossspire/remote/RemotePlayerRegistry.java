package crossspire.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.party.PartyVisibility;

public class RemotePlayerRegistry {

    private static final Map<String, RemotePlayerState> players = new ConcurrentHashMap<String, RemotePlayerState>();

    public static void register(String playerId) {
        if (!players.containsKey(playerId)) {
            players.put(playerId, new RemotePlayerState(playerId));
            BaseMod.logger.info("RemotePlayerRegistry registered: " + playerId.substring(0, 8));
        }
    }

    public static RemotePlayerState get(String playerId) {
        return players.get(playerId);
    }

    public static void remove(String playerId) {
        players.remove(playerId);
        BaseMod.logger.info("RemotePlayerRegistry removed: " + playerId.substring(0, 8));
    }

    public static int count() {
        return players.size();
    }

    public static Collection<RemotePlayerState> all() {
        return new ArrayList<RemotePlayerState>(players.values());
    }

    /** Gameplay projections visible to the local player's current party only. */
    public static Collection<RemotePlayerState> visibleToLocalParty() {
        Collection<String> visibleIds = PartyVisibility.visibleRemotePlayerIds(
            CrossSpireMod.partyManager, CrossSpireMod.playerId, players.keySet());
        Collection<RemotePlayerState> visible = new ArrayList<RemotePlayerState>();
        for (String playerId : visibleIds) {
            RemotePlayerState state = players.get(playerId);
            if (state != null) visible.add(state);
        }
        return visible;
    }

    public static int visibleCountToLocalParty() {
        return visibleToLocalParty().size();
    }

    public static void clear() {
        players.clear();
    }
}
