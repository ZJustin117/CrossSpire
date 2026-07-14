package crossspire.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {

    @Test
    public void shouldSerializeAndDeserializeMonsterIntentMessage() {
        Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
        msg.source = "host-001";
        msg.seq = 42;
        msg.monsterId = "Cultist";
        msg.intent = "ATTACK";
        msg.damage = 6;
        msg.hits = 1;
        msg.targetId = "player-a";

        String json = Protocol.GSON.toJson(msg);
        Protocol.MonsterIntentMessage parsed = Protocol.GSON.fromJson(json, Protocol.MonsterIntentMessage.class);

        assertEquals("monster_intent", parsed.type);
        assertEquals("host-001", parsed.source);
        assertEquals(42, parsed.seq);
        assertEquals("Cultist", parsed.monsterId);
        assertEquals("ATTACK", parsed.intent);
        assertEquals(6, parsed.damage);
        assertEquals(1, parsed.hits);
        assertEquals("player-a", parsed.targetId);
    }

    @Test
    public void shouldSerializeAndDeserializeCombatResultMessage() {
        Protocol.CombatResultMessage msg = new Protocol.CombatResultMessage();
        msg.source = "host-001";
        msg.seq = 43;
        msg.monsterId = "Cultist";

        Protocol.EffectDescription[] effects = new Protocol.EffectDescription[1];
        effects[0] = new Protocol.EffectDescription();
        effects[0].kind = "damage";
        effects[0].target = "player-a";
        effects[0].amount = 6;
        msg.effects = effects;
        msg.operationSequence = new Protocol.OperationStep[0];

        String json = Protocol.GSON.toJson(msg);
        Protocol.CombatResultMessage parsed = Protocol.GSON.fromJson(json, Protocol.CombatResultMessage.class);

        assertEquals("combat_result", parsed.type);
        assertEquals("host-001", parsed.source);
        assertEquals(43, parsed.seq);
        assertEquals("Cultist", parsed.monsterId);
        assertNotNull(parsed.effects);
        assertEquals(1, parsed.effects.length);
        assertEquals("damage", parsed.effects[0].kind);
        assertEquals(6, parsed.effects[0].amount);
        assertEquals("player-a", parsed.effects[0].target);
    }

    @Test
    public void shouldParseCombatResultFromRawJson() {
        String raw = "{\"type\":\"combat_result\",\"source\":\"host\",\"seq\":7,\"monster_id\":\"JawWorm\"," +
                "\"effects\":[{\"kind\":\"damage\",\"target\":\"player-a\",\"amount\":11}]," +
                "\"operation_sequence\":[]}";

        Protocol.CombatResultMessage parsed = Protocol.GSON.fromJson(raw, Protocol.CombatResultMessage.class);

        assertEquals("combat_result", parsed.type);
        assertEquals("JawWorm", parsed.monsterId);
        assertEquals(1, parsed.effects.length);
        assertEquals("damage", parsed.effects[0].kind);
        assertEquals(11, parsed.effects[0].amount);
    }

    @Test
    public void monsterIntentMessageShouldHaveCorrectTypeOnConstruction() {
        Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
        assertEquals("monster_intent", msg.type);
    }

    @Test
    public void combatResultMessageShouldHaveCorrectTypeOnConstruction() {
        Protocol.CombatResultMessage msg = new Protocol.CombatResultMessage();
        assertEquals("combat_result", msg.type);
    }

    @Test
    public void shouldSerializeAndDeserializeEventResultMessage() {
        Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
        msg.source = "host-001";
        msg.seq = 10;
        msg.eventId = "Big Fish";

        Protocol.EffectDescription[] effects = new Protocol.EffectDescription[2];
        effects[0] = new Protocol.EffectDescription();
        effects[0].kind = "heal";
        effects[0].target = "self";
        effects[0].amount = 10;
        effects[1] = new Protocol.EffectDescription();
        effects[1].kind = "obtain_relic";
        effects[1].target = "self";
        effects[1].relicId = "Vajra";
        msg.effects = effects;

        String json = Protocol.GSON.toJson(msg);
        Protocol.EventResultMessage parsed = Protocol.GSON.fromJson(json, Protocol.EventResultMessage.class);

        assertEquals("event_result", parsed.type);
        assertEquals("host-001", parsed.source);
        assertEquals("Big Fish", parsed.eventId);
        assertEquals(2, parsed.effects.length);
        assertEquals("heal", parsed.effects[0].kind);
        assertEquals(10, parsed.effects[0].amount);
        assertEquals("Vajra", parsed.effects[1].relicId);
    }

    @Test
    public void eventResultMessageShouldHaveCorrectTypeOnConstruction() {
        Protocol.EventResultMessage msg = new Protocol.EventResultMessage();
        assertEquals("event_result", msg.type);
    }

    @Test
    public void shouldSerializePlayerEndTurnMessage() {
        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        msg.source = "alice";
        msg.seq = 7;

        String json = Protocol.GSON.toJson(msg);
        Protocol.PlayerEndTurnMessage parsed = Protocol.GSON.fromJson(json, Protocol.PlayerEndTurnMessage.class);

        assertEquals("player_end_turn", parsed.type);
        assertEquals("alice", parsed.source);
        assertEquals(7, parsed.seq);
    }

    @Test
    public void playerEndTurnMessageHasCorrectType() {
        Protocol.PlayerEndTurnMessage msg = new Protocol.PlayerEndTurnMessage();
        assertEquals("player_end_turn", msg.type);
    }

    @Test
    public void shouldSerializeReferenceMigrateMessage() {
        Protocol.ReferenceMigrateMessage msg = new Protocol.ReferenceMigrateMessage();
        msg.source = "alice";
        msg.seq = 5;
        msg.refId = "card:Strike_P@bob";
        msg.resourceType = "card";
        msg.resourceId = "Strike_P";
        msg.resourceHash = "abc123";

        String json = Protocol.GSON.toJson(msg);
        Protocol.ReferenceMigrateMessage parsed = Protocol.GSON.fromJson(json, Protocol.ReferenceMigrateMessage.class);

        assertEquals("reference_migrate", parsed.type);
        assertEquals("alice", parsed.source);
        assertEquals("card:Strike_P@bob", parsed.refId);
        assertEquals("card", parsed.resourceType);
        assertEquals("Strike_P", parsed.resourceId);
        assertEquals("abc123", parsed.resourceHash);
    }
}
