package crossspire.event;

import crossspire.network.Protocol;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure personal-only effect diff for approved native event execution. */
public final class PersonalEventDeltaPlanner {

    private PersonalEventDeltaPlanner() {}

    public static Protocol.EffectDescription[] diff(PersonalPlayerSnapshot before,
                                                    PersonalPlayerSnapshot after) {
        if (before == null || after == null) return new Protocol.EffectDescription[0];
        List<Protocol.EffectDescription> out = new ArrayList<Protocol.EffectDescription>();
        int goldDelta = after.gold - before.gold;
        if (goldDelta != 0) out.add(effect("gain_gold", "self", goldDelta));
        int hpDelta = after.hp - before.hp;
        // Replayer damage() targets monsters; personal HP loss uses lose_hp.
        if (hpDelta < 0) out.add(effect("lose_hp", "self", -hpDelta));
        if (hpDelta > 0) out.add(effect("heal", "self", hpDelta));
        int maxHpDelta = after.maxHp - before.maxHp;
        if (maxHpDelta != 0) out.add(effect("max_hp", "self", maxHpDelta));
        int blockDelta = after.block - before.block;
        if (blockDelta > 0) out.add(effect("gain_block", "self", blockDelta));

        appendInventoryDiffs(out, before.relicIds, after.relicIds, "obtain_relic", "remove_relic");
        appendInventoryDiffs(out, before.potionIds, after.potionIds, "obtain_potion", "remove_potion");
        appendInventoryDiffs(out, before.cardIds, after.cardIds, "obtain_card", "remove_card");

        // Fallback size-only signal when card ID lists are empty on both sides.
        if ((before.cardIds == null || before.cardIds.length == 0)
            && (after.cardIds == null || after.cardIds.length == 0)) {
            int deckDelta = after.masterDeckSize - before.masterDeckSize;
            if (deckDelta != 0) out.add(effect("deck_size", "self", deckDelta));
        }
        return out.toArray(new Protocol.EffectDescription[0]);
    }

    public static Protocol.EventPlayerResultPayload toResult(Protocol.EventChoiceDecisionPayload decision,
                                                             String playerId,
                                                             PersonalPlayerSnapshot before,
                                                             PersonalPlayerSnapshot after) {
        if (decision == null || playerId == null || playerId.isEmpty()) return null;
        if (decision.eventInstanceId == null || decision.eventInstanceId.isEmpty()) return null;
        if (decision.requestId == null || decision.requestId.isEmpty()) return null;
        Protocol.EventPlayerResultPayload result = new Protocol.EventPlayerResultPayload();
        result.eventInstanceId = decision.eventInstanceId;
        result.partyId = decision.partyId;
        result.requestId = decision.requestId;
        result.playerId = playerId;
        result.effects = diff(before, after);
        return result;
    }

    private static void appendInventoryDiffs(List<Protocol.EffectDescription> out,
                                            String[] beforeIds, String[] afterIds,
                                            String obtainKind, String removeKind) {
        Map<String, Integer> beforeCounts = multiset(beforeIds);
        Map<String, Integer> afterCounts = multiset(afterIds);
        for (Map.Entry<String, Integer> e : afterCounts.entrySet()) {
            int delta = e.getValue().intValue() - count(beforeCounts, e.getKey());
            for (int i = 0; i < delta; i++) {
                out.add(idEffect(obtainKind, e.getKey()));
            }
        }
        for (Map.Entry<String, Integer> e : beforeCounts.entrySet()) {
            int delta = e.getValue().intValue() - count(afterCounts, e.getKey());
            for (int i = 0; i < delta; i++) {
                out.add(idEffect(removeKind, e.getKey()));
            }
        }
    }

    private static Protocol.EffectDescription idEffect(String kind, String id) {
        Protocol.EffectDescription e = effect(kind, "self", 1);
        if (kind.indexOf("relic") >= 0) {
            e.relicId = id;
        } else if (kind.indexOf("potion") >= 0) {
            e.potionId = id;
        } else {
            e.cardId = id;
        }
        return e;
    }

    private static Map<String, Integer> multiset(String[] ids) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        if (ids == null) return counts;
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            Integer prev = counts.get(id);
            counts.put(id, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
        }
        return counts;
    }

    private static int count(Map<String, Integer> counts, String id) {
        Integer v = counts.get(id);
        return v == null ? 0 : v.intValue();
    }

    private static Protocol.EffectDescription effect(String kind, String target, int amount) {
        Protocol.EffectDescription e = new Protocol.EffectDescription();
        e.kind = kind;
        e.target = target;
        e.amount = amount;
        return e;
    }
}
