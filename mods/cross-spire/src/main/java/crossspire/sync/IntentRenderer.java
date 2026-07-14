package crossspire.sync;

import basemod.BaseMod;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import crossspire.network.Protocol;

public final class IntentRenderer {

    public static void showSnapshot(Protocol.MonsterIntentEntry[] intents) {
        if (AbstractDungeon.getCurrRoom() == null) return;
        BaseMod.logger.info("IntentRenderer snapshot: " + intents.length + " monsters");
        for (Protocol.MonsterIntentEntry e : intents) {
            for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
                if (e.monsterId.equals(m.id)) {
                    m.createIntent();
                    break;
                }
            }
        }
    }

    public static void show(String monsterId, String intent, int damage, int hits) {
        if (AbstractDungeon.getCurrRoom() == null) return;
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (monsterId.equals(m.id)) {
                m.createIntent();
                BaseMod.logger.info("IntentRenderer set intent for " + monsterId + " -> " + intent);
                break;
            }
        }
    }

    private IntentRenderer() {}
}
