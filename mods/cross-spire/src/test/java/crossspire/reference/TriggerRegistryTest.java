package crossspire.reference;

import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import java.util.List;

public class TriggerRegistryTest {

    @After
    public void tearDown() {
        TriggerRegistry.clear();
    }

    @Test
    public void shouldRegisterAndRetrieveTriggers() {
        Reference<String> ref = new TestReference("test:item@owner", "owner");
        TriggerRegistry.register("onCardUse", "card:Strike_R", ref);

        List<Reference<?>> triggers = TriggerRegistry.getTriggers("onCardUse", "card:Strike_R");
        assertEquals(1, triggers.size());
        assertSame(ref, triggers.get(0));
    }

    @Test
    public void shouldReturnEmptyForUnregisteredTrigger() {
        List<Reference<?>> triggers = TriggerRegistry.getTriggers("onCardUse", "card:UnknownCard");
        assertNotNull(triggers);
        assertEquals(0, triggers.size());
    }

    @Test
    public void shouldSupportWildcardEventType() {
        Reference<String> ref = new TestReference("test:item@owner", "owner");
        TriggerRegistry.register("*", "card:Strike_R", ref);

        List<Reference<?>> triggers = TriggerRegistry.getTriggers("onCardUse", "card:Strike_R");
        assertEquals(1, triggers.size());
    }

    @Test
    public void shouldUnregisterTrigger() {
        Reference<String> ref = new TestReference("test:item@owner", "owner");
        TriggerRegistry.register("onCardUse", "card:Strike_R", ref);
        TriggerRegistry.unregister("onCardUse", "card:Strike_R", ref);

        List<Reference<?>> triggers = TriggerRegistry.getTriggers("onCardUse", "card:Strike_R");
        assertEquals(0, triggers.size());
    }

    @Test
    public void shouldClearAllTriggers() {
        Reference<String> ref1 = new TestReference("ref1@a", "a");
        Reference<String> ref2 = new TestReference("ref2@b", "b");
        TriggerRegistry.register("onCardUse", "card:A", ref1);
        TriggerRegistry.register("atBattleStart", "*", ref2);
        TriggerRegistry.clear();

        assertEquals(0, TriggerRegistry.getTriggers("onCardUse", "card:A").size());
        assertEquals(0, TriggerRegistry.getTriggers("atBattleStart", "*").size());
    }

    @Test
    public void triggerOnShouldRegisterViaConvenienceMethod() {
        Reference<String> ref = new TestReference("card:Strike_R@alice", "alice");
        ref.triggerOn("onCardUse");

        List<Reference<?>> triggers = TriggerRegistry.getTriggers("onCardUse", "card:Strike_R@alice");
        assertEquals(1, triggers.size());
        assertSame(ref, triggers.get(0));
    }

    private static class TestReference extends Reference<String> {
        TestReference(String refId, String ownerId) {
            super(refId, ownerId, Type.LOCAL, "");
        }
        @Override public void dereference(Object... args) {}
    }
}
