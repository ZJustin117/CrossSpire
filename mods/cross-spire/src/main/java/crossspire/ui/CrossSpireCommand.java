package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.ui.buttons.GridSelectConfirmButton;
import crossspire.combat.EventCapture;
import crossspire.combat.EventSyncPatches;
import crossspire.CrossSpireMod;
import crossspire.event.EventApprovalCoordinator;
import crossspire.event.EventChoiceSender;
import crossspire.map.MapDefinition;
import crossspire.map.MapHostVoteSender;
import crossspire.map.MapNode;
import crossspire.map.MapRegisterSender;
import crossspire.map.NodeInstanceHostVoteSender;
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;
import crossspire.network.RoomPinSender;
import crossspire.network.StarConnectionManager;
import crossspire.network.StandardPacket;
import crossspire.network.StageVoteSender;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.sync.FullSnapshotSender;
import crossspire.remote.RemotePlayerState;
import crossspire.sync.QueueSubmitBuilder;
import crossspire.party.PartyState;
import crossspire.party.PartyCoordinator;
import crossspire.party.PartyManager;
import java.util.Arrays;
import java.util.UUID;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrossSpireCommand extends ConsoleCommand {

    @Override
    protected void execute(String[] tokens, int depth) {
        if (tokens.length <= depth) { errorMsg(); return; }
        String sub = tokens[depth].toLowerCase();

        if ("host".equals(sub)) { cmdHost(tokens, depth); }
        else if ("join".equals(sub)) { cmdJoin(tokens, depth); }
        else if ("connect".equals(sub)) { cmdJoin(tokens, depth); }
        else if ("disconnect".equals(sub)) { cmdDisconnect(); }
        else if ("status".equals(sub)) { cmdStatus(); }
        else if ("info".equals(sub)) { cmdInfo(); }
        else if ("lobby".equals(sub)) { cmdLobby(); }
        else if ("combat".equals(sub)) { cmdCombat(); }
        else if ("ready".equals(sub)) { cmdReady(tokens, depth); }
        else if ("start".equals(sub)) { cmdStart(tokens, depth); }
        else if ("queue".equals(sub)) { QueueDisplay.show(); }
        else if ("play".equals(sub)) { cmdPlay(tokens, depth); }
        else if ("room".equals(sub)) { cmdRoomPin(tokens, depth); }
        else if ("snapshot".equals(sub)) { cmdSnapshot(); }
        else if ("vote".equals(sub)) { cmdStageVote(tokens, depth); }
        else if ("select".equals(sub)) { cmdInteractSelect(tokens, depth); }
        else if ("cevent".equals(sub)) { cmdCrossEvent(tokens, depth); }
        else if ("eventsel".equals(sub)) { cmdEventSelect(tokens, depth); }
        else if ("eselect".equals(sub)) { cmdEventCardSelect(tokens, depth); }
        else if ("evote".equals(sub)) { cmdEventVote(tokens, depth); }
        else if ("gamestate".equals(sub)) { cmdGameState(); }
        else if ("confirm".equals(sub)) { cmdConfirm(); }
        else if ("phase".equals(sub)) { cmdPhase(tokens, depth); }
        else if ("party".equals(sub)) { cmdParty(tokens, depth); }
        else if ("maphost".equals(sub)) { cmdMapHostVote(tokens, depth); }
        else if ("mapreg".equals(sub)) { cmdMapRegister(tokens, depth); }
        else if ("nodehost".equals(sub)) { cmdNodeInstanceHostVote(tokens, depth); }
        else if ("eventopen".equals(sub)) { cmdEventOpen(tokens, depth); }
        else if ("eventchoice".equals(sub)) { cmdEventChoice(tokens, depth); }
        else if ("eventresult".equals(sub)) { cmdEventResult(); }
        else { errorMsg(); }
    }

    private void cmdHost(String[] tokens, int depth) {
        if (tokens.length < depth + 3) {
            DevConsole.log("Usage: crossspire host <advertised-ip> <port>");
            return;
        }
        int port = parsePort(tokens[depth + 2]);
        if (port < 0) return;
        String advertisedIp = tokens[depth + 1];
        DevConsole.log("Hosting on " + advertisedIp + ":" + port + "...");
        CrossSpireMod.host(advertisedIp, port);
        if (CrossSpireMod.stageHost != null) {
            CrossSpireMod.stageHost.setStageHost(CrossSpireMod.playerId);
        }
    }

    private void cmdJoin(String[] tokens, int depth) {
        if (tokens.length < depth + 3) {
            DevConsole.log("Usage: crossspire join <ip> <port>");
            return;
        }
        int port = parsePort(tokens[depth + 2]);
        if (port < 0) return;
        String host = tokens[depth + 1];
        DevConsole.log("Joining " + host + ":" + port + "...");
        CrossSpireMod.join(host, port);
    }

    private int parsePort(String value) {
        try {
            return StarConnectionManager.parsePort(value);
        } catch (IllegalArgumentException e) {
            DevConsole.log("Invalid port: " + e.getMessage());
            return -1;
        }
    }

    private void cmdDisconnect() {
        DevConsole.log("Disconnecting...");
        CrossSpireMod.disconnect();
        RemotePlayerRegistry.clear();
    }

    private void cmdStatus() {
        boolean c = CrossSpireMod.isConnected();
        String pid = CrossSpireMod.playerId;
        DevConsole.log("=== CrossSpire Status ===");
        DevConsole.log("Connected: " + (c ? "YES" : "NO"));
        DevConsole.log("PlayerId: " + (pid.isEmpty() ? "(none)" : pid));
        DevConsole.log("Room: " + ServerPicker.roomCode);
        DevConsole.log("Peers: " + (CrossSpireMod.connectionManager != null
            ? CrossSpireMod.connectionManager.connectionCount() : 0));
        DevConsole.log("Queue size: " + (CrossSpireMod.centralQueueManager != null
            ? CrossSpireMod.centralQueueManager.size() : 0));
        DevConsole.log("Combat phase: " + crossspire.combat.CombatPhaseCoordinator.getCurrentPhase());
    }

    private void cmdParty(String[] tokens, int depth) {
        if (tokens.length > depth + 1 && !"status".equalsIgnoreCase(tokens[depth + 1])) {
            DevConsole.log("Usage: crossspire party status");
            return;
        }
        if (CrossSpireMod.partyManager == null) {
            DevConsole.log("Party directory unavailable");
            return;
        }
        DevConsole.log("=== CrossSpire Parties ===");
        BaseMod.logger.info("=== CrossSpire Parties ===");
        for (PartyState party : CrossSpireMod.partyManager.snapshot()) {
            String line = "party=" + party.partyId + " leader=" + party.leaderId
                + " members=" + party.memberIds
                + " phase=" + party.phaseStatus
                + " mapId=" + party.mapInstanceId
                + " pos=" + party.mapPosition
                + " nodeHost=" + party.nodeInstanceHostId
                + " rev=" + party.partyRevision;
            DevConsole.log(line);
            BaseMod.logger.info("CrossSpire party status " + line);
        }
    }

    private static String localPartyId() {
        if (CrossSpireMod.partyManager == null) return PartyManager.DEFAULT_PARTY_ID;
        String partyId = CrossSpireMod.partyManager.getPartyIdForPlayer(CrossSpireMod.playerId);
        return partyId != null ? partyId : PartyManager.DEFAULT_PARTY_ID;
    }

    private void cmdMapHostVote(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire maphost <candidate_player_id>");
            return;
        }
        String candidate = tokens[depth + 1];
        String partyId = localPartyId();
        StandardPacket vote = MapHostVoteSender.buildVote(partyId, candidate);
        String raw = StandardPacket.toJson(vote);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        DevConsole.log("MapHost vote party=" + partyId + " candidate=" + candidate);
        BaseMod.logger.info("CrossSpire maphost vote party=" + partyId + " candidate=" + candidate);
    }

    private void cmdMapRegister(String[] tokens, int depth) {
        String partyId = localPartyId();
        String mapId = tokens.length > depth + 1 ? tokens[depth + 1] : "M1";
        String start = tokens.length > depth + 2 ? tokens[depth + 2] : "start";
        String next = tokens.length > depth + 3 ? tokens[depth + 3] : "node1";
        String nextType = tokens.length > depth + 4 ? tokens[depth + 4] : "monster";
        if (!"monster".equals(nextType) && !"event".equals(nextType)) {
            DevConsole.log("Usage: crossspire mapreg [map_id] [start] [next] [monster|event]");
            return;
        }
        MapDefinition map = new MapDefinition(mapId, "EXORDIUM", 1, "console", start,
            Arrays.asList(
                new MapNode(start, Arrays.asList(next)),
                new MapNode(next, nextType, Arrays.<String>asList())));
        StandardPacket register = MapRegisterSender.buildRegister(
            partyId, CrossSpireMod.playerId, UUID.randomUUID().toString().substring(0, 8), map);
        String raw = StandardPacket.toJson(register);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        DevConsole.log("Map register party=" + partyId + " map=" + mapId
            + " start=" + start + "->" + next + " (" + nextType + ")");
        BaseMod.logger.info("CrossSpire mapreg party=" + partyId + " map=" + mapId
            + " nextType=" + nextType);
    }

    private void cmdNodeInstanceHostVote(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire nodehost <candidate_player_id>");
            return;
        }
        String candidate = tokens[depth + 1];
        String partyId = localPartyId();
        StandardPacket vote = NodeInstanceHostVoteSender.buildVote(partyId, candidate);
        String raw = StandardPacket.toJson(vote);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.route(raw);
        } else {
            CrossSpireMod.send(raw);
        }
        DevConsole.log("NodeInstanceHost vote party=" + partyId + " candidate=" + candidate);
        BaseMod.logger.info("CrossSpire nodehost vote party=" + partyId + " candidate=" + candidate);
    }

    private void cmdPhase(String[] tokens, int depth) {
        if (tokens.length <= depth + 1) {
            DevConsole.log("Combat phase: " + crossspire.combat.CombatPhaseCoordinator.getCurrentPhase());
            DevConsole.log("Usage: crossspire phase <player_turn|resolving_queue|queue_empty|pre_monster_turn|monster_turn|post_monster_turn>");
            return;
        }
        String phase = tokens[depth + 1].toLowerCase();
        if (!crossspire.combat.CombatPhase.isValid(phase)) {
            DevConsole.log("Invalid phase: " + phase);
            return;
        }
        String partyId = localPartyId();
        if (PartyCoordinator.isLeader(CrossSpireMod.partyManager, partyId, CrossSpireMod.playerId)) {
            crossspire.combat.CombatPhaseCoordinator.broadcast(partyId, phase);
            DevConsole.log("Broadcast combat_phase=" + phase + " party=" + partyId);
        } else {
            DevConsole.log("Only party leader can set combat phase (current="
                + crossspire.combat.CombatPhaseCoordinator.getCurrentPhase() + ")");
        }
    }

    private void cmdInfo() {
        cmdStatus();
        DevConsole.log("");
        cmdLobby();
        DevConsole.log("");
        cmdCombat();
        DevConsole.log("");
        QueueDisplay.show();
    }

    private void cmdLobby() {
        DevConsole.log("=== Lobby ===");
        int rc = RemotePlayerRegistry.visibleCountToLocalParty();
        DevConsole.log("Remote players: " + rc);
        if (rc > 0) {
            for (RemotePlayerState rp : RemotePlayerRegistry.visibleToLocalParty()) {
                String cls = rp.characterClass != null && !rp.characterClass.isEmpty() ? rp.characterClass : "?";
                DevConsole.log("  " + rp.playerId.substring(0, 8) + " " + cls + " HP:" + rp.hp + "/" + rp.maxHp + " B:" + rp.block);
                if (rp.powers != null && rp.powers.length > 0) {
                    StringBuilder pw = new StringBuilder("    [");
                    for (int i = 0; i < rp.powers.length; i++) {
                        if (i > 0) pw.append(", ");
                        pw.append(rp.powers[i]).append("x").append(i < rp.powerAmounts.length ? rp.powerAmounts[i] : 1);
                    }
                    pw.append("]");
                    DevConsole.log(pw.toString());
                }
            }
        }
        if (!CrossSpireMod.playerId.isEmpty()) {
            DevConsole.log("StageHost: " + ServerPicker.isStageHost);
        }
    }

    private void cmdCombat() {
        DevConsole.log("=== Combat ===");
        if (AbstractDungeon.player == null) {
            DevConsole.log("Not in game. Use crossspire start first.");
            return;
        }
        DevConsole.log("Mode:  " + CardCrawlGame.mode);
        DevConsole.log("Floor: " + AbstractDungeon.floorNum);
        DevConsole.log("Player: " + AbstractDungeon.player.name
            + " HP:" + AbstractDungeon.player.currentHealth + "/" + AbstractDungeon.player.maxHealth
            + " B:" + AbstractDungeon.player.currentBlock
            + " E:" + AbstractDungeon.player.energy.energy);
        DevConsole.log("Hand:  " + AbstractDungeon.player.hand.size());
        DevConsole.log("Draw:  " + AbstractDungeon.player.drawPile.size());
        DevConsole.log("Disc:  " + AbstractDungeon.player.discardPile.size());

        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                DevConsole.log("Monster: " + m.name + " HP:" + m.currentHealth + "/" + m.maxHealth
                    + " intent:" + (m.intent != null ? m.intent.name() : "?")
                    + " powers:" + m.powers.size());
            }
        }
    }

    private void cmdReady(String[] tokens, int depth) {
        String charName = tokens.length > depth + 1 ? tokens[depth + 1].toUpperCase() : "IRONCLAD";
        DevConsole.log("Ready as " + charName + "...");
        CrossSpireMod.lobbyState.markLocalReady(charName);
    }

    private void cmdStart(String[] tokens, int depth) {
        final String charName = tokens.length > depth + 1 ? tokens[depth + 1].toUpperCase() : "IRONCLAD";
        final String seed = tokens.length > depth + 2 ? tokens[depth + 2] : "";
        DevConsole.log("Starting " + charName + " seed=" + (seed.isEmpty() ? "(auto)" : seed) + " (next frame)");
        // Console / game-probe run off the GL thread. GameStarter mutates CardCrawlGame.mode
        // and dungeon state; concurrent MainMenuScreen.render → FontHelper GlyphLayout IOB.
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                try {
                    crossspire.remote.GameStarter.start(charName, seed.isEmpty() ? null : seed);
                    BaseMod.logger.info("CrossSpire start queued: mode=" + CardCrawlGame.mode
                        + " fading=" + (CardCrawlGame.mainMenuScreen != null
                            && CardCrawlGame.mainMenuScreen.isFadingOut));
                } catch (Exception e) {
                    BaseMod.logger.error("CrossSpire start failed: " + e.getClass().getName() + ": " + e.getMessage());
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    BaseMod.logger.error("CrossSpire start stack: " + sw.toString());
                    DevConsole.log("Start failed: " + e.getMessage());
                }
                // Frame order: this runnable → update sees fadedOut → dungeon built.
                // Re-post so bind runs after that update (next frame).
                Gdx.app.postRunnable(new Runnable() {
                    @Override public void run() {
                        crossspire.remote.GameStarter.bindLocalPlayerIfReady();
                        BaseMod.logger.info("CrossSpire start result: player="
                            + (AbstractDungeon.player != null ? AbstractDungeon.player.name : "null")
                            + " mode=" + CardCrawlGame.mode + " floor=" + AbstractDungeon.floorNum
                            + " dungeon=" + (CardCrawlGame.dungeon != null
                                ? CardCrawlGame.dungeon.getClass().getSimpleName() : "null")
                            + " transition=" + (CardCrawlGame.dungeonTransitionScreen != null));
                    }
                });
            }
        });
    }

    private void cmdPlay(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire play <card_id> [target_monster_id]");
            return;
        }
        String cardId = tokens[depth + 1];
        String targetId = tokens.length > depth + 2 ? tokens[depth + 2] : null;

        if (AbstractDungeon.player == null) {
            DevConsole.log("Not in combat. Use crossspire start first.");
            return;
        }

        AbstractCard card = CardLibrary.getCard(cardId);
        if (card == null) {
            DevConsole.log("Card not found: " + cardId);
            return;
        }

        if (targetId == null) {
            if (card.target == AbstractCard.CardTarget.ENEMY
                || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
                AbstractMonster first = null;
                for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                    if (!m.isDeadOrEscaped()) { first = m; break; }
                }
                targetId = first != null ? first.id : "self";
            } else {
                targetId = "self";
            }
        }

        if (!"self".equals(targetId)) {
            AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.getMonster(targetId);
            if (m == null || m.isDeadOrEscaped()) {
                DevConsole.log("Target not found: " + targetId);
                return;
            }
        }

        Protocol.QueueSubmitMessage pkt = QueueSubmitBuilder.build(cardId, targetId);

        String queueSubmit = Protocol.GSON.toJson(pkt);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            // A host has no loopback socket; use the same authorization/routing handler directly.
            CrossSpireMod.messageRouter.route(queueSubmit);
        } else {
            CrossSpireMod.send(queueSubmit);
        }
        DevConsole.log("Queue submit: " + cardId + " → " + targetId);
    }

    private void cmdRoomPin(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire room <index>");
            return;
        }
        int roomIndex;
        try {
            roomIndex = Integer.parseInt(tokens[depth + 1]);
        } catch (NumberFormatException e) {
            DevConsole.log("Invalid room index: " + tokens[depth + 1]);
            return;
        }
        String partyId = localPartyId();
        String msg = RoomPinSender.buildRoomPin(CrossSpireMod.playerId, partyId, roomIndex);
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.handleRoomPin(msg);
        } else {
            CrossSpireMod.send(msg);
        }
        BaseMod.logger.info("CrossSpire roomPin party=" + partyId + " room=" + roomIndex
            + " host=" + CrossSpireMod.isRoomHost());
        DevConsole.log("Room pin party=" + partyId + " index=" + roomIndex);
    }

    private void cmdSnapshot() {
        String snap = FullSnapshotSender.build();
        CrossSpireMod.send((String) snap);
        DevConsole.log("Full snapshot sent");
    }

    private void cmdStageVote(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire vote <player_id>");
            return;
        }
        String candidate = tokens[depth + 1];
        String msg = StageVoteSender.buildStageVote(CrossSpireMod.playerId, candidate);
        if (CrossSpireMod.isRoomHost()) {
            if (CrossSpireMod.messageRouter != null) {
                CrossSpireMod.messageRouter.handleStageVote(msg);
            }
        } else {
            CrossSpireMod.send((String) msg);
        }
        DevConsole.log("Voted for: " + candidate);
    }

    private void cmdInteractSelect(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire select <card_id>");
            return;
        }
        String cardId = tokens[depth + 1];

        if (AbstractDungeon.player == null) {
            DevConsole.log("Not in game.");
            return;
        }

        GridCardSelectScreen gcs = AbstractDungeon.gridSelectScreen;
        if (gcs == null || gcs.targetGroup == null || gcs.targetGroup.group == null) {
            DevConsole.log("No active card selection.");
            return;
        }

        AbstractCard chosen = null;
        for (AbstractCard c : gcs.targetGroup.group) {
            if (c.cardID.equals(cardId)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            DevConsole.log("Card not in pool: " + cardId);
            return;
        }

        if (gcs.selectedCards.contains(chosen)) {
            DevConsole.log("Already selected: " + cardId);
            return;
        }

        gcs.selectedCards.add(chosen);
        DevConsole.log("Selected: " + cardId + " (" + gcs.selectedCards.size() + " total)");

        if (gcs.confirmButton != null && gcs.selectedCards.size() >= 1) {
            gcs.confirmButton.hb.clicked = true;
            BaseMod.logger.info("CrossSpire select: confirmed " + cardId);
        }
    }

    private void cmdCrossEvent(String[] tokens, int depth) {
        if (tokens.length <= depth + 1) {
            DevConsole.log("Usage: crossspire cevent <event_name...>");
            return;
        }
        StringBuilder eventKey = new StringBuilder(tokens[depth + 1]);
        for (int i = depth + 2; i < tokens.length; i++) {
            eventKey.append(' ').append(tokens[i]);
        }
        String key = eventKey.toString();
        BaseMod.logger.info("CrossSpire cevent key=" + key);
        com.megacrit.cardcrawl.events.AbstractEvent ev = com.megacrit.cardcrawl.helpers.EventHelper.getEvent(key);
        BaseMod.logger.info("CrossSpire cevent getEvent=" + (ev != null ? ev.getClass().getSimpleName() : "null"));
        if (ev == null) {
            DevConsole.log("Unknown event: " + key);
            return;
        }
        com.megacrit.cardcrawl.rooms.EventRoom room = new com.megacrit.cardcrawl.rooms.EventRoom();
        room.event = ev;
        com.megacrit.cardcrawl.dungeons.AbstractDungeon.currMapNode.room = room;
        com.megacrit.cardcrawl.dungeons.AbstractDungeon.screen = com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen.NONE;
        if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.effectList != null) com.megacrit.cardcrawl.dungeons.AbstractDungeon.effectList.clear();
        if (com.megacrit.cardcrawl.dungeons.AbstractDungeon.topLevelEffects != null) com.megacrit.cardcrawl.dungeons.AbstractDungeon.topLevelEffects.clear();
        ev.onEnterRoom();
        BaseMod.logger.info("CrossSpire cevent entered: " + ev.getClass().getSimpleName());
        lastEventClass = ev.getClass().getName();
        broadcastEventInterface(ev);
        DevConsole.log("Entered event: " + key);
    }

    private static void broadcastEventInterface(com.megacrit.cardcrawl.events.AbstractEvent ev) {
        if (CrossSpireMod.connectionManager == null) return;

        String eventId = ev.getClass().getSimpleName();
        String description = "";
        try {
            java.lang.reflect.Field bodyField = com.megacrit.cardcrawl.events.AbstractEvent.class.getDeclaredField("body");
            bodyField.setAccessible(true);
            Object bodyVal = bodyField.get(ev);
            if (bodyVal instanceof String) description = (String) bodyVal;
        } catch (Exception ignored) {}

        String[] optionTexts = new String[0];
        boolean[] disabled = new boolean[0];
        try {
            java.lang.reflect.Field optionsField = ev.getClass().getField("OPTIONS");
            optionTexts = (String[]) optionsField.get(null);
            disabled = new boolean[optionTexts.length];
        } catch (Exception ignored) {}

        if (optionTexts.length > 0) {
            String msg = EventMessageSender.buildEventInterface(
                eventId, ev.getClass().getName(), description, optionTexts, disabled);
            CrossSpireMod.send((String) msg);
            BaseMod.logger.info("CrossSpire cevent event_interface: " + eventId + " options=" + optionTexts.length
                + " peers=" + CrossSpireMod.connectionManager.connectionCount());
        }
    }

    private static String lastEventClass = "";
    private static String approvalEventInstanceId = "";
    private static String approvalEventHash = "";
    private static String approvalRequestId = "";

    public static void setLastEventClass(String cls) { lastEventClass = cls; }

    private void cmdEventOpen(String[] tokens, int depth) {
        String partyId = localPartyId();
        String eventId = tokens.length > depth + 1 ? tokens[depth + 1] : "BigFish";
        Protocol.EventInterfacePayload iface = new Protocol.EventInterfacePayload();
        iface.eventInstanceId = "evt:" + partyId + ":" + UUID.randomUUID().toString().substring(0, 8);
        iface.partyId = partyId;
        iface.eventId = eventId;
        iface.eventClass = "diagnostic." + eventId;
        iface.resourceHash = "diagnostic-" + eventId;
        iface.name = eventId;
        iface.description = "T7.4 diagnostic event";
        iface.mode = EventApprovalCoordinator.MODE_INDIVIDUAL;
        Protocol.EventOptionInfo option = new Protocol.EventOptionInfo();
        option.index = 0;
        option.text = "Choose";
        option.enabled = true;
        iface.options = new Protocol.EventOptionInfo[] {option};
        approvalEventInstanceId = iface.eventInstanceId;
        approvalEventHash = iface.resourceHash;
        StandardPacket packet = EventChoiceSender.interfacePacket(iface);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost()) CrossSpireMod.messageRouter.route(raw);
        else CrossSpireMod.send(raw);
        BaseMod.logger.info("CrossSpire eventopen party=" + partyId + " event=" + iface.eventInstanceId);
    }

    private void cmdEventChoice(String[] tokens, int depth) {
        if (tokens.length < depth + 2 || approvalEventInstanceId.isEmpty()) {
            DevConsole.log("Usage: crossspire eventchoice <option>; run eventopen first");
            return;
        }
        Protocol.EventChoiceRequestPayload request = new Protocol.EventChoiceRequestPayload();
        request.eventInstanceId = approvalEventInstanceId;
        request.partyId = localPartyId();
        request.requestId = "req:" + UUID.randomUUID().toString().substring(0, 8);
        request.uiStep = "buttonEffect";
        request.optionIndex = Integer.parseInt(tokens[depth + 1]);
        request.resourceHash = approvalEventHash;
        approvalRequestId = request.requestId;
        StandardPacket packet = EventChoiceSender.requestPacket(request);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost()) CrossSpireMod.messageRouter.route(raw);
        else CrossSpireMod.send(raw);
        BaseMod.logger.info("CrossSpire eventchoice request=" + approvalRequestId);
    }

    private void cmdEventResult() {
        if (approvalEventInstanceId.isEmpty() || approvalRequestId.isEmpty()) {
            DevConsole.log("Run eventopen then eventchoice first");
            return;
        }
        Protocol.EventPlayerResultPayload result = new Protocol.EventPlayerResultPayload();
        result.eventInstanceId = approvalEventInstanceId;
        result.partyId = localPartyId();
        result.requestId = approvalRequestId;
        result.playerId = CrossSpireMod.playerId;
        result.effects = new Protocol.EffectDescription[0];
        StandardPacket packet = EventChoiceSender.resultPacket(result);
        String raw = StandardPacket.toJson(packet);
        if (CrossSpireMod.isRoomHost()) CrossSpireMod.messageRouter.route(raw);
        else CrossSpireMod.send(raw);
        BaseMod.logger.info("CrossSpire eventresult request=" + approvalRequestId);
    }

    private void cmdEventSelect(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire eventsel <option_index>");
            return;
        }
        int idx = Integer.parseInt(tokens[depth + 1]);

        if (lastEventClass.isEmpty()) {
            DevConsole.log("No event active — wait for event_interface first");
            return;
        }

        String eventShort = lastEventClass.contains(".") 
            ? lastEventClass.substring(lastEventClass.lastIndexOf('.') + 1) 
            : lastEventClass;

        EventCapture.startTranscript(eventShort);
        EventCapture.appendButtonEffect(idx);

        String transcript = EventCapture.buildTranscript();
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.handleEventTranscript(transcript);
        } else {
            CrossSpireMod.send((String) transcript);
        }
        BaseMod.logger.info("eventsel sandbox transcript: " + eventShort + " option=" + idx);
        DevConsole.log("Event sandbox: option " + idx + " → transcript sent");
    }

    private void cmdEventCardSelect(String[] tokens, int depth) {
        if (tokens.length < depth + 3) {
            DevConsole.log("Usage: crossspire eselect <option_index> <card_id> [card_id2...]");
            return;
        }
        int optionIdx = Integer.parseInt(tokens[depth + 1]);

        String eventShort = lastEventClass.contains(".") 
            ? lastEventClass.substring(lastEventClass.lastIndexOf('.') + 1) 
            : lastEventClass;

        int cardCount = tokens.length - depth - 2;
        String[] cardIds = new String[cardCount];
        for (int i = 0; i < cardCount; i++) cardIds[i] = tokens[depth + 2 + i];

        EventCapture.startTranscript(eventShort);
        EventCapture.appendButtonEffect(optionIdx);
        EventCapture.appendCardSelect(cardIds);
        EventCapture.appendConfirm();

        String transcript = EventCapture.buildTranscript();
        if (CrossSpireMod.isRoomHost() && CrossSpireMod.messageRouter != null) {
            CrossSpireMod.messageRouter.handleEventTranscript(transcript);
            BaseMod.logger.info("eselect routed locally to messageRouter");
        } else {
            CrossSpireMod.send((String) transcript);
        }
        BaseMod.logger.info("eselect full transcript: " + eventShort + " option=" + optionIdx + " cards=" + cardCount);
        DevConsole.log("Event select+cards: option " + optionIdx + " + " + cardCount + " cards → sent");
    }

    private void cmdEventVote(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire evote <option_index>");
            return;
        }
        int idx = Integer.parseInt(tokens[depth + 1]);
        com.google.gson.JsonObject vote = new com.google.gson.JsonObject();
        vote.addProperty("type", "event_vote");
        vote.addProperty("source", CrossSpireMod.playerId);
        vote.addProperty("option_index", idx);
        CrossSpireMod.send((String) new com.google.gson.Gson().toJson(vote));
        DevConsole.log("Event vote: option " + idx);
    }

    private void cmdGameState() {
        if (AbstractDungeon.player == null) {
            DevConsole.log("Not in game. Use crossspire start first.");
            return;
        }

        com.megacrit.cardcrawl.characters.AbstractPlayer p = AbstractDungeon.player;
        StringBuilder sb = new StringBuilder();
        sb.append("=== Game State ===\n");
        sb.append("HP: ").append(p.currentHealth).append('/').append(p.maxHealth)
            .append("  Block: ").append(p.currentBlock).append('\n');
        sb.append("Gold: ").append(p.gold)
            .append("  Energy: ").append(p.energy != null ? p.energy.energy : 0).append('\n');

        if (AbstractDungeon.getCurrRoom() != null) {
            sb.append("Room: ").append(AbstractDungeon.getCurrRoom().getClass().getSimpleName())
                .append("  Floor: ").append(AbstractDungeon.floorNum)
                .append("  Act: ").append(AbstractDungeon.actNum).append('\n');
            if (AbstractDungeon.getCurrRoom().event != null) {
                sb.append("Event: ").append(AbstractDungeon.getCurrRoom().event.getClass().getSimpleName()).append('\n');
            }
        }

        sb.append("Relics (").append(p.relics.size()).append("): ");
        for (int i = 0; i < p.relics.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(p.relics.get(i).relicId);
        }
        sb.append('\n');

        sb.append("Potions (").append(p.potions.size()).append("): ");
        for (int i = 0; i < p.potions.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(p.potions.get(i).ID);
        }
        sb.append('\n');

        sb.append("MasterDeck (").append(p.masterDeck.size()).append("):\n");
        java.util.LinkedHashMap<String, Integer> deckCounts = new java.util.LinkedHashMap<>();
        for (com.megacrit.cardcrawl.cards.AbstractCard c : p.masterDeck.group) {
            String key = c.cardID + (c.upgraded ? "+" : "");
            deckCounts.put(key, deckCounts.getOrDefault(key, 0) + 1);
        }
        int col = 0;
        for (java.util.Map.Entry<String, Integer> e : deckCounts.entrySet()) {
            if (col > 0) sb.append(", ");
            sb.append(e.getKey());
            if (e.getValue() > 1) sb.append('x').append(e.getValue());
            col++;
            if (col % 4 == 0) sb.append('\n');
        }
        sb.append('\n');

        if (p.hand != null && !p.hand.isEmpty()) {
            sb.append("Hand (").append(p.hand.size()).append("): ");
            for (int i = 0; i < p.hand.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(p.hand.group.get(i).cardID);
            }
            sb.append('\n');
        }

        sb.append("Draw: ").append(p.drawPile.size())
            .append("  Discard: ").append(p.discardPile.size())
            .append("  Exhaust: ").append(p.exhaustPile.size()).append('\n');

        if (p.powers != null && !p.powers.isEmpty()) {
            sb.append("Powers: ");
            for (int i = 0; i < p.powers.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(p.powers.get(i).ID).append('x').append(p.powers.get(i).amount);
            }
            sb.append('\n');
        }

        String output = sb.toString();
        DevConsole.log(output);
        BaseMod.logger.info("CrossSpire gamestate:\n" + output);
    }

    private void cmdConfirm() {
        Gdx.app.postRunnable(new Runnable() {
            @Override public void run() {
                EventSyncPatches.clickConfirm();
                AbstractDungeon.gridSelectScreen.update();
                AbstractDungeon.isScreenUp = false;
                if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().event != null) {
                    AbstractDungeon.getCurrRoom().event.update();
                }
                BaseMod.logger.info("crossspire confirm + update done");
            }
        });
        DevConsole.log("Confirm sent (next frame)");
    }

    @Override
    public void errorMsg() {
        DevConsole.log("crossspire: host <advertised-ip> <port> | join <ip> <port> | disconnect | status | info | lobby | combat | gamestate | ready [char] | start [char] [seed] | play <card> [target] | queue | room <index> | phase [name] | snapshot | vote <player> | select <card> | cevent <name> | eventsel <index> | eselect <index> <card> | evote <index> | confirm");
    }
}
