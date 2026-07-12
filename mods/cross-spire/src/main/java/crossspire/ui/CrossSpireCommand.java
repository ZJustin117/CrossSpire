package crossspire.ui;

import basemod.BaseMod;
import basemod.DevConsole;
import basemod.devcommands.ConsoleCommand;
import com.google.gson.JsonObject;
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
        } else if ("start".equals(sub)) {
            cmdStart(tokens, depth);
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
            String usedSeed = crossspire.remote.GameStarter.start(charName, seed.isEmpty() ? null : seed);
            if (usedSeed != null) {
                CrossSpireMod.lastStartedChar = charName;
                CrossSpireMod.lastStartedSeed = usedSeed;
                CrossSpireMod.startedGame = true;
                if (ServerPicker.isStageHost) {
                    broadcastBattleStart(charName, usedSeed);
                }
            }
        } catch (Exception e) {
            DevConsole.log("Start failed: " + e.getMessage());
        }
    }

    private void broadcastBattleStart(String charName, String seed) {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
        Protocol.StageSync msg = new Protocol.StageSync();
        msg.character = charName;
        msg.seed = seed;
        msg.source = CrossSpireMod.playerId;
        msg.seq = 1;
        msg.act = 1;
        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(msg));
        BaseMod.logger.info("CrossSpireCommand broadcast stage_sync: " + charName + " seed=" + seed);
    }

    @Override
    public void errorMsg() {
        DevConsole.log("crossspire: connect <url> <room> | disconnect | status | start [char] [seed]");
    }
}
