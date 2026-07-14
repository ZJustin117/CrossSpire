package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;
import crossspire.remote.RemotePlayerRegistry;
import crossspire.remote.RemotePlayerState;

public class CrossSpireCommand extends ConsoleCommand {

    @Override
    protected void execute(String[] tokens, int depth) {
        if (tokens.length <= depth) { errorMsg(); return; }
        String sub = tokens[depth].toLowerCase();

        if ("connect".equals(sub)) { cmdConnect(tokens, depth); }
        else if ("disconnect".equals(sub)) { cmdDisconnect(); }
        else if ("status".equals(sub)) { cmdStatus(); }
        else if ("info".equals(sub)) { cmdInfo(); }
        else if ("lobby".equals(sub)) { cmdLobby(); }
        else if ("combat".equals(sub)) { cmdCombat(); }
        else if ("ready".equals(sub)) { cmdReady(tokens, depth); }
        else if ("start".equals(sub)) { cmdStart(tokens, depth); }
        else if ("queue".equals(sub)) { QueueDisplay.show(); }
        else if ("play".equals(sub)) { cmdPlay(tokens, depth); }
        else { errorMsg(); }
    }

    private void cmdConnect(String[] tokens, int depth) {
        if (tokens.length < depth + 3) {
            DevConsole.log("Usage: crossspire connect <url> <room_code>");
            return;
        }
        ServerPicker.serverUrl = tokens[depth + 1];
        ServerPicker.roomCode = tokens[depth + 2];
        DevConsole.log("Connecting to " + ServerPicker.serverUrl + " room " + ServerPicker.roomCode);
        CrossSpireMod.connect();
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
        DevConsole.log("Seed:  " + (CrossSpireMod.syncedSeed != null ? CrossSpireMod.syncedSeed : "(none)"));
        DevConsole.log("P2P peers: " + CrossSpireMod.p2pManager.connectionCount());
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
            DevConsole.log("Synced seed: " + (CrossSpireMod.syncedSeed != null ? CrossSpireMod.syncedSeed : "(none)"));
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
        } catch (Exception e) {
            DevConsole.log("Start failed: " + e.getMessage());
        }
    }

    private void cmdPlay(String[] tokens, int depth) {
        if (tokens.length < depth + 2) {
            DevConsole.log("Usage: crossspire play <card_id> [target_monster_id]");
            return;
        }
        String cardId = tokens[depth + 1];
        String targetId = tokens.length > depth + 2 ? tokens[depth + 2] : "self";

        if (AbstractDungeon.player == null || AbstractDungeon.actionManager == null) {
            DevConsole.log("Not in combat. Use crossspire start first.");
            return;
        }

        AbstractCard card = CardLibrary.getCard(cardId);
        if (card == null) {
            DevConsole.log("Card not found: " + cardId);
            return;
        }

        AbstractCreature target;
        if ("self".equals(targetId)) {
            target = AbstractDungeon.player;
        } else {
            AbstractMonster m = AbstractDungeon.getCurrRoom().monsters.getMonster(targetId);
            if (m == null || m.isDeadOrEscaped()) {
                DevConsole.log("Target not found: " + targetId);
                return;
            }
            target = m;
        }

        AbstractCard copy = card.makeCopy();
        copy.current_x = AbstractDungeon.player.hb.cX;
        copy.current_y = AbstractDungeon.player.hb.cY;

        AbstractDungeon.actionManager.addToBottom(new UseCardAction(copy, target));
        DevConsole.log("Playing " + cardId + " → " + targetId);
    }

    @Override
    public void errorMsg() {
        DevConsole.log("crossspire: connect <url> <room> | disconnect | status | info | lobby | combat | ready [char] | start [char] [seed] | play <card> [target] | queue");
    }
}
