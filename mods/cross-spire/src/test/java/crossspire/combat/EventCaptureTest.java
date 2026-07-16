package crossspire.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class EventCaptureTest {

    @Test
    public void shouldBuildEmptyTranscript() {
        EventCapture.startTranscript("LivingWall");
        String json = EventCapture.buildTranscript();
        assertTrue(json.contains("event_transcript"));
        assertTrue(json.contains("LivingWall"));
        assertTrue(json.contains("\"actions\""));
    }

    @Test
    public void shouldAppendButtonEffectStep() {
        EventCapture.startTranscript("BigFish");
        EventCapture.appendButtonEffect(0);
        String json = EventCapture.buildTranscript();
        assertTrue(json.contains("\"type\":\"buttonEffect\""));
        assertTrue(json.contains("\"index\":0"));
    }

    @Test
    public void shouldAppendCardSelectStep() {
        EventCapture.startTranscript("LivingWall");
        EventCapture.appendCardSelect(new String[]{"Strike_R", "Defend_R"});
        String json = EventCapture.buildTranscript();
        assertTrue(json.contains("\"type\":\"cardSelect\""));
        assertTrue(json.contains("Strike_R"));
        assertTrue(json.contains("Defend_R"));
    }

    @Test
    public void shouldAppendConfirmStep() {
        EventCapture.startTranscript("Event");
        EventCapture.appendConfirm();
        assertTrue(EventCapture.buildTranscript().contains("\"type\":\"confirm\""));
    }

    @Test
    public void shouldResetTranscriptBetweenEvents() {
        EventCapture.startTranscript("A");
        EventCapture.appendButtonEffect(0);
        assertFalse(EventCapture.buildTranscript().isEmpty());

        EventCapture.startTranscript("B");
        String json = EventCapture.buildTranscript();
        assertTrue(json.contains("B"));
        assertFalse(json.contains("A"));
    }
}
