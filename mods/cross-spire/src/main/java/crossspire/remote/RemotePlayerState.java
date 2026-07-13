package crossspire.remote;

public class RemotePlayerState {

    public final String playerId;
    public int hp;
    public int maxHp;
    public int block;
    public int energy;
    public int gold;
    public String characterClass;
    public String[] powers;
    public int[] powerAmounts;

    public RemotePlayerState(String playerId) {
        this.playerId = playerId;
        this.hp = 80;
        this.maxHp = 80;
        this.characterClass = "";
        this.powers = new String[0];
        this.powerAmounts = new int[0];
    }
}
