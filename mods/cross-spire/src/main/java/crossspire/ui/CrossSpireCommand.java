package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
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

        if ("connect".equals(sub)) {
            cmdConnect(tokens, depth);
        } else if ("disconnect".equals(sub)) {
            cmdDisconnect();
        } else if ("status".equals(sub)) {
            cmdStatus();
        } else if ("ready".equals(sub)) {
            cmdReady(tokens, depth);
        } else if ("start".equals(sub)) {
            cmdStart(tokens, depth);
        } else if ("queue".equals(sub)) {
            QueueDisplay.show();
        } else if ("play".equals(sub)) {
            cmdPlay(tokens, depth);
        } else if ("auto".equals(sub)) {
            cmdAuto(tokens, depth);
        } else {
            errorMsg();
        }
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
        DevConsole.log("Connected: " + CrossSpireMod.isConnected());
        String pid = CrossSpireMod.playerId;
        DevConsole.log("PlayerId: " + (pid.isEmpty() ? "(none)" : pid.substring(0, 8)));
        DevConsole.log("StageHost: " + ServerPicker.isStageHost);
        DevConsole.log("Remote: " + RemotePlayerRegistry.count());
        for (RemotePlayerState rp : RemotePlayerRegistry.all()) {
            DevConsole.log("  " + rp.playerId.substring(0, 8) + " HP:" + rp.hp + "/" + rp.maxHp);
        }
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

    private void cmdReady(String[] tokens, int depth) {
        String charName = tokens.length > depth + 1 ? tokens[depth + 1].toUpperCase() : "IRONCLAD";
        DevConsole.log("Ready as " + charName + "...");
        CrossSpireMod.lobbyState.markLocalReady(charName);
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
        CrossSpireMod.queueManager.enqueueOwnCard(cardId, targetId);
        DevConsole.log("Playing " + cardId + " → " + targetId);
    }

    private void cmdAuto(String[] tokens, int depth) {
        String charName = tokens.length > depth + 1 ? tokens[depth + 1].toUpperCase() : "IRONCLAD";
        DevConsole.log("Auto starting " + charName + "...");
        crossspire.sync.AutoGameStartPatch.pendingChar = charName;
        new com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen().open(false);
    }

    @Override
    public void errorMsg() {
        DevConsole.log("crossspire: connect <url> <room> | ready [char] | status | start [char] [seed] | play <card> [target] | queue");
    }
}
