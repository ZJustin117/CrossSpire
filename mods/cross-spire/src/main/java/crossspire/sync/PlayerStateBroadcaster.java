package crossspire.sync;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import crossspire.CrossSpireMod;
import crossspire.network.Protocol;

public final class PlayerStateBroadcaster {

    private PlayerStateBroadcaster() {}

    static Protocol.PlayerStateMessage build(int hp, int maxHp, int block, int energy, int gold, String charClass) {
        Protocol.PlayerStateMessage msg = new Protocol.PlayerStateMessage();
        msg.source = CrossSpireMod.playerId;
        msg.seq = CrossSpireMod.nextSeq();
        msg.player = new Protocol.RemotePlayerState();
        msg.player.hp = hp;
        msg.player.maxHp = maxHp;
        msg.player.block = block;
        msg.player.energy = energy;
        msg.player.gold = gold;
        msg.player.characterClass = charClass;
        return msg;
    }

    public static void broadcast(AbstractPlayer player) {
        if (!CrossSpireMod.isConnected()) return;
        int energy = player.energy != null ? player.energy.energy : 0;
        Protocol.PlayerStateMessage msg = build(
            player.currentHealth, player.maxHealth, player.currentBlock,
            energy, player.gold, player.getClass().getSimpleName()
        );
        CrossSpireMod.send(Protocol.GSON.toJson(msg));
    }
}
