package crossspire.combat;

import crossspire.network.Protocol;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;

public class EffectCaptureTest {

    @After
    public void tearDown() {
        EffectCapture.stopCapture();
    }

    @Test
    public void shouldCaptureAndRetrieveEffects() {
        EffectCapture.startCapture();
        EffectCapture.record("damage", "monster_0", 6);
        EffectCapture.record("gain_block", "self", 5);

        Protocol.EffectDescription[] effects = EffectCapture.getCaptured();
        assertNotNull("effects should not be null", effects);
        assertEquals("should capture 2 effects", 2, effects.length);
        assertEquals("first effect kind", "damage", effects[0].kind);
        assertEquals("first effect amount", 6, effects[0].amount);
        assertEquals("first effect target", "monster_0", effects[0].target);
        assertEquals("second effect kind", "gain_block", effects[1].kind);
    }

    @Test
    public void shouldClearAfterStopCapture() {
        EffectCapture.startCapture();
        EffectCapture.record("damage", "monster_0", 3);
        EffectCapture.stopCapture();

        Protocol.EffectDescription[] effects = EffectCapture.getCaptured();
        assertEquals("effects should be empty after stopCapture", 0, effects.length);
    }

    @Test
    public void shouldCaptureEffectsWithExtendedFields() {
        EffectCapture.startCapture();
        EffectCapture.record("apply_power", "self", 3, "power_id", "Vulnerable");
        EffectCapture.record("obtain_relic", "self", 1, "relic_id", "Vajra");
        EffectCapture.record("exhaust_card", "self", 1, "card_id", "Strike_R");

        Protocol.EffectDescription[] effects = EffectCapture.getCaptured();
        assertEquals("should capture 3 effects", 3, effects.length);
        assertEquals("apply_power", effects[0].kind);
        assertEquals("Vulnerable", effects[0].powerId);
        assertEquals("Vajra", effects[1].relicId);
        assertEquals("Strike_R", effects[2].cardId);
    }

    @Test
    public void shouldIgnoreWhenNotCapturing() {
        EffectCapture.startCapture();
        EffectCapture.stopCapture();
        EffectCapture.record("damage", "monster_0", 10);
        Protocol.EffectDescription[] effects = EffectCapture.getCaptured();
        assertEquals("should have no effects when not capturing", 0, effects.length);
    }
}
