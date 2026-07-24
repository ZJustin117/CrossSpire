package crossspire.event;

import crossspire.network.Protocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PersonalEventDeltaPlannerTest {

    @Test
    public void diffsGoldHpAndBlockIntoEffects() {
        PersonalPlayerSnapshot before = snap(99, 80, 80, 0, 10);
        PersonalPlayerSnapshot after = snap(109, 70, 80, 5, 9);

        Protocol.EffectDescription[] effects = PersonalEventDeltaPlanner.diff(before, after);

        assertNotNull(effects);
        assertTrue(effects.length >= 3);
        assertEquals("gain_gold", find(effects, "gain_gold").kind);
        assertEquals(10, find(effects, "gain_gold").amount);
        assertEquals("lose_hp", find(effects, "lose_hp").kind);
        assertEquals(10, find(effects, "lose_hp").amount);
        assertEquals("gain_block", find(effects, "gain_block").kind);
        assertEquals(5, find(effects, "gain_block").amount);
    }

    @Test
    public void emptyWhenUnchanged() {
        PersonalPlayerSnapshot s = snap(50, 40, 40, 0, 10);
        Protocol.EffectDescription[] effects = PersonalEventDeltaPlanner.diff(s, s);
        assertNotNull(effects);
        assertEquals(0, effects.length);
    }

    @Test
    public void buildsResultPayloadFromDecisionAndDiff() {
        Protocol.EventChoiceDecisionPayload decision = new Protocol.EventChoiceDecisionPayload();
        decision.eventInstanceId = "E1";
        decision.partyId = "P0";
        decision.requestId = "req-1";
        decision.optionIndex = 0;
        decision.uiStep = "buttonEffect";

        Protocol.EventPlayerResultPayload result = PersonalEventDeltaPlanner.toResult(
            decision, "alice", snap(10, 50, 50, 0, 5), snap(20, 50, 50, 0, 5));

        assertNotNull(result);
        assertEquals("alice", result.playerId);
        assertEquals("req-1", result.requestId);
        assertEquals(1, result.effects.length);
        assertEquals("gain_gold", result.effects[0].kind);
        assertNull(PersonalEventDeltaPlanner.toResult(null, "alice", snap(1, 1, 1, 0, 1), snap(2, 1, 1, 0, 1)));
    }

    @Test
    public void diffsRelicPotionAndCardInventory() {
        PersonalPlayerSnapshot before = inv(
            new String[] {"Burning Blood"},
            new String[] {"Potion of Strength"},
            new String[] {"Strike_R", "Defend_R"});
        PersonalPlayerSnapshot after = inv(
            new String[] {"Burning Blood", "Vajra"},
            new String[] {},
            new String[] {"Strike_R", "Defend_R", "Bash"});

        Protocol.EffectDescription[] effects = PersonalEventDeltaPlanner.diff(before, after);
        assertEquals("Vajra", find(effects, "obtain_relic").relicId);
        assertEquals("Potion of Strength", find(effects, "remove_potion").potionId);
        assertEquals("Bash", find(effects, "obtain_card").cardId);
    }

    @Test
    public void removesCardsAndRelicsByIdentity() {
        PersonalPlayerSnapshot before = inv(
            new String[] {"Vajra", "Burning Blood"},
            new String[0],
            new String[] {"Strike_R", "Strike_R", "Defend_R"});
        PersonalPlayerSnapshot after = inv(
            new String[] {"Burning Blood"},
            new String[0],
            new String[] {"Strike_R", "Defend_R"});

        Protocol.EffectDescription[] effects = PersonalEventDeltaPlanner.diff(before, after);
        assertEquals("Vajra", find(effects, "remove_relic").relicId);
        assertEquals("Strike_R", find(effects, "remove_card").cardId);
    }

    private static PersonalPlayerSnapshot snap(int gold, int hp, int maxHp, int block, int deck) {
        return new PersonalPlayerSnapshot(gold, hp, maxHp, block, deck);
    }

    private static PersonalPlayerSnapshot inv(String[] relics, String[] potions, String[] cards) {
        return new PersonalPlayerSnapshot(0, 50, 50, 0,
            cards != null ? cards.length : 0, relics, potions, cards);
    }

    private static Protocol.EffectDescription find(Protocol.EffectDescription[] effects, String kind) {
        for (Protocol.EffectDescription e : effects) {
            if (kind.equals(e.kind)) return e;
        }
        throw new AssertionError("missing " + kind);
    }
}
