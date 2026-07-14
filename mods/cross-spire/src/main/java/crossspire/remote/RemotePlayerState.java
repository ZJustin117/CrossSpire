package crossspire.remote;

import crossspire.resource.RemoteCharacterResource;

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
    public String currentAnimation = "Idle";
    private RemoteCharacterResource characterResource;
    private RemotePlayer playerInstance;

    public RemotePlayerState(String playerId) {
        this.playerId = playerId;
        this.hp = 80;
        this.maxHp = 80;
        this.characterClass = "";
        this.powers = new String[0];
        this.powerAmounts = new int[0];
    }

    public RemoteCharacterResource getCharacterResource() {
        return characterResource;
    }

    public void setCharacterResource(RemoteCharacterResource chr) {
        this.characterResource = chr;
    }

    public RemotePlayer getPlayerInstance() {
        if (playerInstance == null && characterClass != null && !characterClass.isEmpty()) {
            try {
                com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass pc =
                    com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.valueOf(characterClass);
                playerInstance = new RemotePlayer("Remote", pc, playerId);
                playerInstance.currentHealth = hp;
                playerInstance.maxHealth = maxHp;
                playerInstance.currentBlock = block;
                playerInstance.drawX = 400;
                playerInstance.drawY = 300;
            } catch (Exception ignored) {}
        }
        return playerInstance;
    }

    public void syncToPlayerInstance() {
        if (playerInstance == null) return;
        playerInstance.currentHealth = hp;
        playerInstance.maxHealth = maxHp;
        playerInstance.currentBlock = block;
    }
}
