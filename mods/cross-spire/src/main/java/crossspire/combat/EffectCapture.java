package crossspire.combat;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.List;

public final class EffectCapture {

    private static final List<Protocol.EffectDescription> captured = new ArrayList<>();
    private static boolean capturing = false;

    private EffectCapture() {}

    public static void startCapture() {
        captured.clear();
        capturing = true;
    }

    public static Protocol.EffectDescription[] stopCapture() {
        capturing = false;
        Protocol.EffectDescription[] result = captured.toArray(new Protocol.EffectDescription[0]);
        captured.clear();
        return result;
    }

    public static void record(String kind, String target, int amount) {
        if (!capturing) return;
        Protocol.EffectDescription eff = new Protocol.EffectDescription();
        eff.kind = kind;
        eff.target = target;
        eff.amount = amount;
        captured.add(eff);
    }

    public static void record(String kind, String target, int amount, String fieldName, String fieldValue) {
        if (!capturing) return;
        Protocol.EffectDescription eff = new Protocol.EffectDescription();
        eff.kind = kind;
        eff.target = target;
        eff.amount = amount;
        switch (fieldName) {
            case "power_id": eff.powerId = fieldValue; break;
            case "card_id":  eff.cardId = fieldValue;  break;
            case "relic_id": eff.relicId = fieldValue; break;
            case "potion_id": eff.potionId = fieldValue; break;
        }
        captured.add(eff);
    }

    /** Record apply_power with applier-first logic_owner_id. */
    public static void recordApplyPower(String target, int amount, String powerId, String logicOwnerId) {
        if (!capturing) return;
        Protocol.EffectDescription eff = new Protocol.EffectDescription();
        eff.kind = "apply_power";
        eff.target = target;
        eff.amount = amount;
        eff.powerId = powerId;
        eff.logicOwnerId = logicOwnerId;
        captured.add(eff);
    }

    public static Protocol.EffectDescription[] getCaptured() {
        return captured.toArray(new Protocol.EffectDescription[0]);
    }

    public static boolean isCapturing() {
        return capturing;
    }
}
