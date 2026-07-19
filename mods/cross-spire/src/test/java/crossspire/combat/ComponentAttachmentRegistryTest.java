package crossspire.combat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ComponentAttachmentRegistryTest {

    @Before
    public void setUp() {
        ComponentAttachmentRegistry.clear();
    }

    @After
    public void tearDown() {
        ComponentAttachmentRegistry.clear();
    }

    @Test
    public void registerAndGetByHost() {
        ComponentAttachment a = ComponentAttachmentRegistry.registerApplyPower(
            "Vulnerable", "player-a", "monster:Cultist", 2);
        assertNotNull(a);
        assertEquals(1, ComponentAttachmentRegistry.size());
        assertEquals("Vulnerable", a.powerId);
        assertEquals("player-a", a.logicOwnerId);
        assertEquals("monster:Cultist", a.hostEntityId);
        assertEquals(2, a.amount);

        List<ComponentAttachment> onHost = ComponentAttachmentRegistry.getByHost("monster:Cultist");
        assertEquals(1, onHost.size());
        assertEquals(a.instanceId, onHost.get(0).instanceId);
    }

    @Test
    public void reapplyUpdatesSameInstance() {
        ComponentAttachment first = ComponentAttachmentRegistry.registerApplyPower(
            "Vulnerable", "player-a", "monster:Cultist", 2);
        ComponentAttachment second = ComponentAttachmentRegistry.registerApplyPower(
            "Vulnerable", "player-a", "monster:Cultist", 3);
        assertEquals(first.instanceId, second.instanceId);
        assertEquals(1, ComponentAttachmentRegistry.size());
        assertEquals(3, ComponentAttachmentRegistry.get(first.instanceId).amount);
    }

    @Test
    public void differentOwnersAreSeparateAttachments() {
        ComponentAttachmentRegistry.registerApplyPower("Vulnerable", "player-a", "monster:M", 1);
        ComponentAttachmentRegistry.registerApplyPower("Vulnerable", "player-b", "monster:M", 2);
        assertEquals(2, ComponentAttachmentRegistry.size());
        assertEquals(1, ComponentAttachmentRegistry.getByLogicOwner("player-a").size());
        assertEquals(1, ComponentAttachmentRegistry.getByLogicOwner("player-b").size());
        assertEquals(2, ComponentAttachmentRegistry.getByHost("monster:M").size());
    }

    @Test
    public void hostEntityIdForTargetMapsSelfAndMonster() {
        assertEquals("player:local", ComponentAttachmentRegistry.hostEntityIdForTarget("self"));
        assertEquals("player:local", ComponentAttachmentRegistry.hostEntityIdForTarget(null));
        assertEquals("monster:Cultist", ComponentAttachmentRegistry.hostEntityIdForTarget("Cultist"));
        assertEquals("monster:x", ComponentAttachmentRegistry.hostEntityIdForTarget("monster:x"));
        assertEquals("player:p1", ComponentAttachmentRegistry.hostEntityIdForTarget("player:p1"));
    }

    @Test
    public void removeAndClear() {
        ComponentAttachment a = ComponentAttachmentRegistry.registerApplyPower(
            "Weak", "player-a", "monster:M", 1);
        assertTrue(ComponentAttachmentRegistry.remove(a.instanceId));
        assertEquals(0, ComponentAttachmentRegistry.size());
        ComponentAttachmentRegistry.registerApplyPower("Weak", "player-a", "monster:M", 1);
        ComponentAttachmentRegistry.clear();
        assertEquals(0, ComponentAttachmentRegistry.size());
    }

    @Test
    public void rejectEmptyPowerOrHost() {
        assertNull(ComponentAttachmentRegistry.registerApplyPower("", "a", "monster:M", 1));
        assertNull(ComponentAttachmentRegistry.registerApplyPower("Vulnerable", "a", "", 1));
        assertNull(ComponentAttachmentRegistry.register(null));
    }
}
