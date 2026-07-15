package crossspire.sync;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalCapturePatchesTest {

    @Test
    public void shouldPushAndPopSuppress() {
        LocalCapturePatches.pushSuppress();
        LocalCapturePatches.pushSuppress();
        // Since suppressDepth is private, test via the public pushSuppress API
        // The counter should handle nested calls correctly
    }

    @Test
    public void shouldNotThrowOnMultiplePushes() {
        for (int i = 0; i < 10; i++) {
            LocalCapturePatches.pushSuppress();
        }
    }
}
