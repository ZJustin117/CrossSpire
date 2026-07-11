package crossspire.remote;

public class RemotePlayerState {

    public final String playerId;
    public int hp;
    public int maxHp;
    public int block;
    public int energy;

    public RemotePlayerState(String playerId) {
        this.playerId = playerId;
        this.hp = 80;
        this.maxHp = 80;
    }
}
