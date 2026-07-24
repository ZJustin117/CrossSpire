package crossspire.ui;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.google.gson.JsonObject;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.party.PartyCoordinator;
import crossspire.party.PartyManager;
import crossspire.party.PartyRunStartPlanner;
import crossspire.party.PartyState;
import crossspire.remote.GameStarter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class LobbyState {

    private final Set<String> readyPlayers = new CopyOnWriteArraySet<String>();
    private final Map<String, String> characterChoices = new ConcurrentHashMap<String, String>();
    private int roomSize = 1;
    private boolean started = false;
    private String pendingReadyCharacter = null;
    private String myCharacter = "IRONCLAD";
    private String lastRunSeed = null;

    public String getMyCharacter() { return myCharacter; }

    public boolean isStarted() { return started; }

    public boolean isLocalReady() {
        return CrossSpireMod.playerId != null && readyPlayers.contains(CrossSpireMod.playerId);
    }

    public Set<String> getReadyPlayers() {
        return Collections.unmodifiableSet(readyPlayers);
    }

    public Map<String, String> getCharacterChoices() {
        return Collections.unmodifiableMap(characterChoices);
    }

    public String getLastRunSeed() { return lastRunSeed; }

    private static String safeSub(String s) {
        return s == null ? "?" : s.length() >= 8 ? s.substring(0, 8) : s;
    }

    public void markLocalReady(String character) {
        myCharacter = character.toUpperCase();

        if (CrossSpireMod.isConnected()) {
            readyPlayers.add(CrossSpireMod.playerId);
            characterChoices.put(CrossSpireMod.playerId, myCharacter);
            broadcastAndCheck(character);
        } else {
            pendingReadyCharacter = character.toUpperCase();
            BaseMod.logger.info("LobbyState ready deferred (not connected yet): " + character);
        }
    }

    public void onRoomJoined(int size) {
        roomSize = Math.max(1, size);
        BaseMod.logger.info("LobbyState room joined, size=" + roomSize);
        flushPending();
    }

    public void onPlayerJoined(String playerId) {
        roomSize++;
        BaseMod.logger.info("LobbyState player_joined: " + safeSub(playerId) + " size=" + roomSize);
        flushPending();
        resendOwnReady();
        checkAllReady();
    }

    public void onPlayerLeft(String playerId) {
        roomSize = Math.max(1, roomSize - 1);
        readyPlayers.remove(playerId);
        characterChoices.remove(playerId);
        BaseMod.logger.info("LobbyState player_left: " + safeSub(playerId) + " size=" + roomSize);
    }

    private void resendOwnReady() {
        if (CrossSpireMod.playerId.isEmpty()) return;
        if (!readyPlayers.contains(CrossSpireMod.playerId)) return;
        if (!CrossSpireMod.isConnected()) return;

        String character = characterChoices.get(CrossSpireMod.playerId);
        if (character == null) character = "IRONCLAD";

        Protocol.PlayerReady ready = new Protocol.PlayerReady();
        ready.source = CrossSpireMod.playerId;
        ready.seq = CrossSpireMod.nextSeq();
        ready.character = character;

        CrossSpireMod.send(Protocol.GSON.toJson(ready));
        BaseMod.logger.info("LobbyState resent player_ready as " + character);
    }

    public void onPlayerReady(String rawMessage) {
        JsonObject msg = Protocol.GSON.fromJson(rawMessage, JsonObject.class);
        String source = msg.has("source") ? msg.get("source").getAsString() : "";
        String character = msg.has("character") ? msg.get("character").getAsString() : "IRONCLAD";

        if (source.isEmpty() || source.equals(CrossSpireMod.playerId)) return;

        readyPlayers.add(source);
        characterChoices.put(source, character.toUpperCase());

        BaseMod.logger.info("LobbyState " + safeSub(source) + " ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    private void flushPending() {
        if (pendingReadyCharacter != null && CrossSpireMod.isConnected() && !CrossSpireMod.playerId.isEmpty()) {
            String c = pendingReadyCharacter;
            pendingReadyCharacter = null;
            readyPlayers.add(CrossSpireMod.playerId);
            characterChoices.put(CrossSpireMod.playerId, c);
            broadcastAndCheck(c);
        }
    }

    private void broadcastAndCheck(String character) {
        Protocol.PlayerReady ready = new Protocol.PlayerReady();
        ready.source = CrossSpireMod.playerId;
        ready.seq = CrossSpireMod.nextSeq();
        ready.character = character.toUpperCase();

        CrossSpireMod.send(Protocol.GSON.toJson(ready));
        BaseMod.logger.info("LobbyState sent player_ready as " + character
            + " ready=" + readyPlayers.size() + "/" + roomSize);

        checkAllReady();
    }

    /** Elect stage host when all ready; does not start the run (T7.7a uses party_run_start). */
    private void checkAllReady() {
        if (started) return;
        if (roomSize < 2) return;
        if (readyPlayers.size() < roomSize) return;

        BaseMod.logger.info("LobbyState ALL READY! ready=" + readyPlayers.size());

        String hostId = CrossSpireMod.stageHost.electHost(
            readyPlayers.toArray(new String[0])
        );

        CrossSpireMod.stageHost.setStageHost(hostId);

        BaseMod.logger.info("LobbyState stageHost=" + safeSub(hostId)
            + " (self=" + safeSub(CrossSpireMod.playerId) + ") awaiting party_run_start");
    }

    /**
     * Coordinated play/start (US-1a). Offline: local GameStarter only.
     * Online: require all party members ready; RoomHost builds and broadcasts party_run_start.
     *
     * @return null on success/queued, otherwise a short reject reason for console
     */
    public String requestPartyRunStart(String characterOverride, String seedOverride) {
        if (characterOverride != null && !characterOverride.isEmpty()) {
            myCharacter = characterOverride.toUpperCase();
            if (CrossSpireMod.playerId != null && !CrossSpireMod.playerId.isEmpty()) {
                characterChoices.put(CrossSpireMod.playerId, myCharacter);
                readyPlayers.add(CrossSpireMod.playerId);
            }
        }

        if (!CrossSpireMod.isConnected()) {
            final String ch = myCharacter;
            final String seed = seedOverride != null && !seedOverride.isEmpty() ? seedOverride : null;
            Gdx.app.postRunnable(new Runnable() {
                @Override public void run() {
                    GameStarter.start(ch, seed);
                    GameStarter.bindLocalPlayerIfReady();
                }
            });
            started = true;
            return null;
        }

        PartyState party = localParty();
        Collection<String> members = party != null
            ? party.memberIds
            : readyPlayers;
        if (party == null && (members == null || members.size() < 2)) {
            return "no_party";
        }

        String reason = PartyRunStartPlanner.rejectReason(
            members, readyPlayers, CrossSpireMod.playerId, started);
        if (reason != null) {
            BaseMod.logger.info("LobbyState reject party_run_start: " + reason
                + " ready=" + readyPlayers.size() + " members=" + members.size());
            return reason;
        }

        if (CrossSpireMod.isRoomHost()) {
            return authorizeAndBroadcastRunStart(
                party != null ? party.partyId : PartyManager.DEFAULT_PARTY_ID,
                seedOverride, CrossSpireMod.playerId, members);
        }

        Protocol.PartyRunStartRequest req = new Protocol.PartyRunStartRequest();
        req.source = CrossSpireMod.playerId;
        req.seq = CrossSpireMod.nextSeq();
        req.partyId = party != null ? party.partyId : PartyManager.DEFAULT_PARTY_ID;
        req.seed = seedOverride != null ? seedOverride : "";
        req.character = myCharacter;
        CrossSpireMod.send(Protocol.GSON.toJson(req));
        BaseMod.logger.info("LobbyState sent party_run_start_request party=" + req.partyId);
        return null;
    }

    /**
     * RoomHost: validate request from any member (or local self) and broadcast party_run_start.
     */
    public String authorizeAndBroadcastRunStart(String partyId, String seedOverride,
                                                String requesterId, Collection<String> members) {
        if (!CrossSpireMod.isRoomHost()) return "not_room_host";
        if (started) return "already_started";

        PartyState party = CrossSpireMod.partyManager != null
            ? CrossSpireMod.partyManager.getParty(
                partyId != null && !partyId.isEmpty() ? partyId : PartyManager.DEFAULT_PARTY_ID)
            : null;
        Collection<String> memberIds = members;
        if (party != null) {
            memberIds = party.memberIds;
            partyId = party.partyId;
        }
        if (memberIds == null || memberIds.isEmpty()) return "no_party";

        String reason = PartyRunStartPlanner.rejectReason(
            memberIds, readyPlayers, requesterId, started);
        if (reason != null) {
            BaseMod.logger.info("LobbyState reject authorize party_run_start: " + reason);
            return reason;
        }

        String leaderId = party != null
            ? party.leaderId
            : PartyCoordinator.leaderId(CrossSpireMod.partyManager, partyId);
        if (leaderId == null || leaderId.isEmpty()) leaderId = CrossSpireMod.playerId;

        Protocol.PartyRunStart msg = PartyRunStartPlanner.build(
            partyId, seedOverride, 1, leaderId, CrossSpireMod.playerId,
            memberIds, characterChoices);
        msg.seq = CrossSpireMod.nextSeq();
        lastRunSeed = msg.seed;
        started = true;

        String json = Protocol.GSON.toJson(msg);
        CrossSpireMod.send(json);
        BaseMod.logger.info("LobbyState broadcast party_run_start seed=" + msg.seed
            + " members=" + msg.members.length + " leader=" + safeSub(leaderId));
        // Apply locally (host is also a party member).
        applyPartyRunStart(json);
        return null;
    }

    public void onPartyRunStartRequest(String rawMessage) {
        if (!CrossSpireMod.isRoomHost()) return;
        Protocol.PartyRunStartRequest req =
            Protocol.GSON.fromJson(rawMessage, Protocol.PartyRunStartRequest.class);
        if (req == null || req.source == null || req.source.isEmpty()) return;
        if (req.character != null && !req.character.isEmpty()) {
            characterChoices.put(req.source, req.character.toUpperCase());
            readyPlayers.add(req.source);
        }
        String reject = authorizeAndBroadcastRunStart(
            req.partyId, req.seed, req.source, null);
        if (reject != null) {
            BaseMod.logger.info("LobbyState party_run_start_request from "
                + safeSub(req.source) + " rejected: " + reject);
        }
    }

    public void applyPartyRunStart(String rawMessage) {
        Protocol.PartyRunStart msg =
            Protocol.GSON.fromJson(rawMessage, Protocol.PartyRunStart.class);
        if (msg == null || msg.seed == null || msg.seed.isEmpty()) {
            BaseMod.logger.info("LobbyState ignore empty party_run_start");
            return;
        }
        started = true;
        lastRunSeed = msg.seed;
        final String localChar = PartyRunStartPlanner.characterFor(msg, CrossSpireMod.playerId);
        myCharacter = localChar;
        if (CrossSpireMod.playerId != null) {
            characterChoices.put(CrossSpireMod.playerId, localChar);
            readyPlayers.add(CrossSpireMod.playerId);
        }
        if (msg.members != null) {
            for (Protocol.PartyRunMember m : msg.members) {
                if (m != null && m.playerId != null) {
                    readyPlayers.add(m.playerId);
                    if (m.character != null) characterChoices.put(m.playerId, m.character.toUpperCase());
                }
            }
        }
        BaseMod.logger.info("LobbyState apply party_run_start seed=" + msg.seed
            + " localChar=" + localChar + " act=" + msg.act);
        final String seed = msg.seed;
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                try {
                    GameStarter.start(localChar, seed);
                    BaseMod.logger.info("LobbyState GameStarter queued char=" + localChar
                        + " seed=" + seed);
                } catch (Exception e) {
                    BaseMod.logger.error("LobbyState GameStarter failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                Gdx.app.postRunnable(new Runnable() {
                    @Override public void run() {
                        GameStarter.bindLocalPlayerIfReady();
                    }
                });
            }
        });
    }

    private static PartyState localParty() {
        if (CrossSpireMod.partyManager == null || CrossSpireMod.playerId == null) return null;
        String partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        if (partyId == null || partyId.isEmpty()) partyId = PartyManager.DEFAULT_PARTY_ID;
        return CrossSpireMod.partyManager.getParty(partyId);
    }
}
