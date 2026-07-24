package crossspire.event;

/** Narrow personal economy/inventory fields used for event personal-delta capture. */
public final class PersonalPlayerSnapshot {

    public final int gold;
    public final int hp;
    public final int maxHp;
    public final int block;
    public final int masterDeckSize;
    public final String[] relicIds;
    public final String[] potionIds;
    public final String[] cardIds;

    public PersonalPlayerSnapshot(int gold, int hp, int maxHp, int block, int masterDeckSize) {
        this(gold, hp, maxHp, block, masterDeckSize, null, null, null);
    }

    public PersonalPlayerSnapshot(int gold, int hp, int maxHp, int block, int masterDeckSize,
                                  String[] relicIds, String[] potionIds, String[] cardIds) {
        this.gold = gold;
        this.hp = hp;
        this.maxHp = maxHp;
        this.block = block;
        this.masterDeckSize = masterDeckSize;
        this.relicIds = relicIds != null ? relicIds.clone() : new String[0];
        this.potionIds = potionIds != null ? potionIds.clone() : new String[0];
        this.cardIds = cardIds != null ? cardIds.clone() : new String[0];
    }
}
