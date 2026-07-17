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
import crossspire.network.EventMessageSender;
import crossspire.network.Protocol;
import crossspire.network.RoomPinSender;
import crossspire.network.StageVoteSender;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.sync.FullSnapshotSender;
import crossspire.remote.RemotePlayerState;
import crossspire.sync.QueueSubmitBuilder;
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
        else { errorMsg(); }
    }

    private void cmdHost(String[] tokens, int depth) {
        int port = tokens.length > depth + 1 ? Integer.parseInt(tokens[depth + 1]) : 54321;
        ServerPicker.isRoomHost = true;
        DevConsole.log("Hosting on port " + port + "...");
        System.setProperty("crossspire.p2p.port", String.valueOf(port));
        CrossSpireMod.connect();
        if (CrossSpireMod.stageHost != null) {
            CrossSpireMod.stageHost.setStageHost(CrossSpireMod.playerId);
        }
    }

    private void cmdJoin(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire join <ip> [port]");
            return;
        }
        ServerPicker.hostIp = tokens[depth + 1];
        ServerPicker.hostPort = tokens.length > depth + 2 ? Integer.parseInt(tokens[depth + 2]) : 54321;
        ServerPicker.isRoomHost = false;
        DevConsole.log("Joining " + ServerPicker.hostIp + ":" + ServerPicker.hostPort + "...");
        CrossSpireMod.connect();
    }

    private void cmdConnect(String[] tokens, int depth) { cmdJoin(tokens, depth); }

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
        DevConsole.log("Peers: " + CrossSpireMod.connectionManager.connectionCount());
        DevConsole.log("Queue size: " + CrossSpireMod.centralQueueManager.size());
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
        int rc = RemotePlayerRegistry.count();
        DevConsole.log("Remote players: " + rc);
        if (rc > 0) {
            for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
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
        String charName = tokens.length > depth + 1 ? tokens[depth + 1].toUpperCase() : "IRONCLAD";
        String seed = tokens.length > depth + 2 ? tokens[depth + 2] : "";
        DevConsole.log("Starting " + charName + " seed=" + (seed.isEmpty() ? "(auto)" : seed));
        try {
            crossspire.remote.GameStarter.start(charName, seed.isEmpty() ? null : seed);
            BaseMod.logger.info("CrossSpire start result: player=" + (AbstractDungeon.player != null ? AbstractDungeon.player.name : "null")
                + " mode=" + CardCrawlGame.mode + " floor=" + AbstractDungeon.floorNum);
        } catch (Exception e) {
            BaseMod.logger.error("CrossSpire start failed: " + e.getClass().getName() + ": " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            BaseMod.logger.error("CrossSpire start stack: " + sw.toString());
            DevConsole.log("Start failed: " + e.getMessage());
        }
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

        if (CrossSpireMod.isRoomHost()) {
            CrossSpireMod.centralQueueManager.onQueueSubmit(pkt);
            DevConsole.log("Queue submit (host): " + cardId + " → " + targetId);
            BaseMod.logger.info("CrossSpire cmdPlay HOST: submit " + cardId + "→" + targetId + " queueSize=" + CrossSpireMod.centralQueueManager.size());
        } else {
            CrossSpireMod.send((String) Protocol.GSON.toJson(pkt));
            DevConsole.log("Queue submit (client): " + cardId + " → " + targetId);
        }
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
        String msg = RoomPinSender.buildRoomPin(CrossSpireMod.playerId, roomIndex);
        if (CrossSpireMod.isRoomHost()) {
            if (CrossSpireMod.messageRouter != null) {
                CrossSpireMod.messageRouter.handleRoomPin(msg);
            }
        } else {
            CrossSpireMod.send((String) msg);
        }
        BaseMod.logger.info("CrossSpire roomPin: " + roomIndex + " host=" + CrossSpireMod.isRoomHost());
        DevConsole.log("Room pin: " + roomIndex);
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

    public static void setLastEventClass(String cls) { lastEventClass = cls; }

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
        DevConsole.log("crossspire: host [port] | join <ip> [port] | disconnect | status | info | lobby | combat | confirm | gamestate | ready [char] | start [char] [seed] | play <card> [target] | queue | room <index> | snapshot | vote <player> | select <card> | cevent <name> | eventsel <index> | eselect <index> <card> | evote <index>");
        DevConsole.log("crossspire: host [port] | join <ip> [port] | disconnect | status | info | lobby | combat | ready [char] | start [char] [seed] | play <card> [target] | queue | room <index> | snapshot | vote <player> | select <card> | cevent <name> | eventsel <index> | eselect <card> | evote <index>");
    }
}
