package crossspire.map;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import crossspire.network.StandardPacket;
import crossspire.party.PartyState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Captures and registers a generated STS map only from the unanimously elected MapHost. */
public final class MapHostRegistrationCoordinator {

    private static final Map<String, String> electedHosts = new HashMap<String, String>();
    private static final Map<String, Long> lastNotReadyLogMs = new HashMap<String, Long>();
    private static final Map<String, Boolean> registrationInFlight = new HashMap<String, Boolean>();

    private MapHostRegistrationCoordinator() {}

    private static synchronized boolean shouldLogNotReady(String partyId) {
        long now = System.currentTimeMillis();
        Long last = lastNotReadyLogMs.get(partyId);
        if (last != null && now - last < 2000L) return false;
        lastNotReadyLogMs.put(partyId, now);
        return true;
    }

    public static synchronized void rememberElection(String partyId, String electedMapHostId) {
        if (partyId != null && !partyId.isEmpty() && electedMapHostId != null && !electedMapHostId.isEmpty()) {
            electedHosts.put(partyId, electedMapHostId);
        }
    }

    static synchronized String electedHost(String partyId) {
        return electedHosts.get(partyId);
    }

    static synchronized void clearElection(String partyId) {
        electedHosts.remove(partyId);
    }

    public static void registerIfElected(String partyId, String electedMapHostId) {
        rememberElection(partyId, electedMapHostId);
        if (!CrossSpireMod.isConnected() || CrossSpireMod.partyManager == null
            || !CrossSpireMod.playerId.equals(electedMapHostId)) {
            return;
        }
        PartyState party = CrossSpireMod.partyManager.getParty(partyId);
        if (party == null) return;
        if (!party.mapInstanceId.isEmpty()) {
            clearElection(partyId);
            clearInFlight(partyId);
            return;
        }
        // T7.7b: capture only after local GAMEPLAY so join clients can share topology.
        if (com.megacrit.cardcrawl.core.CardCrawlGame.mode
                != com.megacrit.cardcrawl.core.CardCrawlGame.GameMode.GAMEPLAY
            || com.megacrit.cardcrawl.dungeons.AbstractDungeon.player == null) {
            if (shouldLogNotReady(partyId)) {
                BaseMod.logger.info("MapHost capture deferred until GAMEPLAY party=" + partyId
                    + " mode=" + com.megacrit.cardcrawl.core.CardCrawlGame.mode);
            }
            return;
        }
        if (!beginInFlight(partyId)) return;
        String mapInstanceId = partyId + "-" + UUID.randomUUID().toString();
        MapDefinition map = StsMapDefinitionCapture.capture(mapInstanceId, 1, "sts-map");
        if (map == null) {
            clearInFlight(partyId);
            // Pending elections retry from MapPatches; avoid per-frame spam.
            if (shouldLogNotReady(partyId)) {
                BaseMod.logger.info("MapHost map not ready for capture party=" + partyId);
            }
            return;
        }
        // Clear before routing so concurrent dungeon updates cannot re-capture a second map.
        clearElection(partyId);
        StandardPacket register = MapRegisterSender.buildRegister(partyId, electedMapHostId,
            UUID.randomUUID().toString(), map);
        String raw = StandardPacket.toJson(register);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        clearInFlight(partyId);
        BaseMod.logger.info("MapHost registered captured topology party=" + partyId
            + " map=" + map.mapInstanceId + " nodes=" + map.nodes().size());
    }

    private static synchronized boolean beginInFlight(String partyId) {
        if (Boolean.TRUE.equals(registrationInFlight.get(partyId))) return false;
        registrationInFlight.put(partyId, Boolean.TRUE);
        return true;
    }

    private static synchronized void clearInFlight(String partyId) {
        registrationInFlight.remove(partyId);
    }

    /** Called once vanilla has initialized the map entry node after an earlier pending election. */
    public static void registerPendingElections() {
        Map<String, String> pending;
        synchronized (MapHostRegistrationCoordinator.class) {
            pending = new HashMap<String, String>(electedHosts);
        }
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            registerIfElected(entry.getKey(), entry.getValue());
        }
    }
}
