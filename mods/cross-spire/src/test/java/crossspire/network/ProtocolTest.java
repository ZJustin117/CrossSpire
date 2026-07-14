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

    @Test
    public void shouldSerializeQueueSubmitMessage() {
        Protocol.QueueSubmitMessage msg = new Protocol.QueueSubmitMessage();
        msg.source = "alice";
        msg.seq = 1;
        msg.senderId = "alice";
        msg.ownerId = "bob";
        msg.cardId = "Strike_R";
        msg.resourceHash = "abc123";
        msg.gameTarget = "monster_0";
        msg.target = "host-001";
        msg.timestamp = 1700000000000L;

        String json = Protocol.GSON.toJson(msg);
        Protocol.QueueSubmitMessage parsed = Protocol.GSON.fromJson(json, Protocol.QueueSubmitMessage.class);

        assertEquals("queue_submit", parsed.type);
        assertEquals("alice", parsed.source);
        assertEquals("alice", parsed.senderId);
        assertEquals("bob", parsed.ownerId);
        assertEquals("Strike_R", parsed.cardId);
        assertEquals("abc123", parsed.resourceHash);
        assertEquals("monster_0", parsed.gameTarget);
        assertEquals("host-001", parsed.target);
        assertEquals(1700000000000L, parsed.timestamp);
    }

    @Test
    public void shouldSerializeQueueUpdateMessage() {
        Protocol.QueueEntry entry1 = new Protocol.QueueEntry();
        entry1.packetId = "alice/1";
        entry1.senderId = "alice";
        entry1.ownerId = "alice";
        entry1.cardId = "Strike_R";
        entry1.target = "monster_0";
        entry1.status = "executing";

        Protocol.QueueEntry entry2 = new Protocol.QueueEntry();
        entry2.packetId = "bob/2";
        entry2.senderId = "bob";
        entry2.ownerId = "bob";
        entry2.cardId = "Defend_G";
        entry2.target = "self";
        entry2.status = "pending";

        Protocol.QueueUpdateMessage msg = new Protocol.QueueUpdateMessage();
        msg.source = "host";
        msg.seq = 3;
        msg.entries = new Protocol.QueueEntry[]{entry1, entry2};

        String json = Protocol.GSON.toJson(msg);
        Protocol.QueueUpdateMessage parsed = Protocol.GSON.fromJson(json, Protocol.QueueUpdateMessage.class);

        assertEquals("queue_update", parsed.type);
        assertEquals("host", parsed.source);
        assertEquals(2, parsed.entries.length);
        assertEquals("alice/1", parsed.entries[0].packetId);
        assertEquals("executing", parsed.entries[0].status);
        assertEquals("bob/2", parsed.entries[1].packetId);
        assertEquals("pending", parsed.entries[1].status);
    }

    @Test
    public void shouldSerializeQueueEmptyMessage() {
        Protocol.QueueEmptyMessage msg = new Protocol.QueueEmptyMessage();
        msg.source = "host";
        msg.seq = 5;

        String json = Protocol.GSON.toJson(msg);
        Protocol.QueueEmptyMessage parsed = Protocol.GSON.fromJson(json, Protocol.QueueEmptyMessage.class);

        assertEquals("queue_empty", parsed.type);
        assertEquals("host", parsed.source);
        assertEquals(5, parsed.seq);
    }

    @Test
    public void operationStepShouldSerializePlayCardWithVfx() {
        Protocol.OperationStep step = new Protocol.OperationStep();
        step.step = "play_card";
        step.cardId = "Strike_R";
        step.source = "alice";
        step.target = "monster_0";

        String json = Protocol.GSON.toJson(step);
        Protocol.OperationStep parsed = Protocol.GSON.fromJson(json, Protocol.OperationStep.class);

        assertEquals("play_card", parsed.step);
        assertEquals("Strike_R", parsed.cardId);
        assertEquals("alice", parsed.source);
        assertEquals("monster_0", parsed.target);
    }

    @Test
    public void operationStepShouldSerializeDamageEffect() {
        Protocol.OperationStep step = new Protocol.OperationStep();
        step.step = "damage";
        step.target = "monster_0";
        step.amount = 6;
        step.vfxKind = "SLASH_HORIZONTAL";

        String json = Protocol.GSON.toJson(step);
        Protocol.OperationStep parsed = Protocol.GSON.fromJson(json, Protocol.OperationStep.class);

        assertEquals("damage", parsed.step);
        assertEquals("monster_0", parsed.target);
        assertEquals(6, parsed.amount);
        assertEquals("SLASH_HORIZONTAL", parsed.vfxKind);
    }

    @Test
    public void shouldParseQueueSubmitFromRawJson() {
        String raw = "{\"type\":\"queue_submit\",\"source\":\"alice\",\"seq\":2," +
                "\"sender_id\":\"alice\",\"owner_id\":\"bob\"," +
                "\"card_id\":\"Strike_R\",\"resource_hash\":\"abc123\"," +
                "\"game_target\":\"monster_0\",\"target\":\"host-001\",\"timestamp\":1700000000000}";

        Protocol.QueueSubmitMessage parsed = Protocol.GSON.fromJson(raw, Protocol.QueueSubmitMessage.class);

        assertEquals("queue_submit", parsed.type);
        assertEquals("alice", parsed.senderId);
        assertEquals("bob", parsed.ownerId);
        assertEquals("Strike_R", parsed.cardId);
        assertEquals("monster_0", parsed.gameTarget);
        assertEquals("host-001", parsed.target);
    }

    @Test
    public void shouldSerializeMonsterIntentSnapshotAsOptionalField() {
        Protocol.MonsterIntentMessage msg = new Protocol.MonsterIntentMessage();
        msg.source = "host";
        msg.seq = 1;
        msg.monsterId = "Cultist";

        Protocol.MonsterIntentEntry e1 = new Protocol.MonsterIntentEntry();
        e1.monsterId = "Cultist";
        e1.intent = "ATTACK";
        e1.damage = 6;
        e1.hits = 1;
        e1.targetId = "self";

        Protocol.MonsterIntentEntry e2 = new Protocol.MonsterIntentEntry();
        e2.monsterId = "JawWorm";
        e2.intent = "DEFEND";
        e2.damage = 0;
        e2.hits = 1;
        e2.targetId = "self";

        msg.intents = new Protocol.MonsterIntentEntry[]{e1, e2};

        String json = Protocol.GSON.toJson(msg);
        Protocol.MonsterIntentMessage parsed = Protocol.GSON.fromJson(json, Protocol.MonsterIntentMessage.class);
        // intents array present indicates snapshot mode
        assertNotNull(parsed.intents);
        assertEquals(2, parsed.intents.length);
        assertEquals("Cultist", parsed.intents[0].monsterId);
        assertEquals("ATTACK", parsed.intents[0].intent);
        assertEquals(6, parsed.intents[0].damage);
        assertEquals("JawWorm", parsed.intents[1].monsterId);
    }
}
