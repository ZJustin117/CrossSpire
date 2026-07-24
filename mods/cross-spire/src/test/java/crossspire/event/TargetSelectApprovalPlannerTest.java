package crossspire.event;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TargetSelectApprovalPlannerTest {

    @Test
    public void extractsNonEmptyTargetIds() {
        String[] ids = TargetSelectApprovalPlanner.targetIds(
            Arrays.asList("Strike_R", "", null, "Defend_R"));
        assertEquals(2, ids.length);
        assertEquals("Strike_R", ids[0]);
        assertEquals("Defend_R", ids[1]);
        assertEquals(0, TargetSelectApprovalPlanner.targetIds(Collections.<String>emptyList()).length);
    }

    @Test
    public void gatesOnlyBoundConfirmWithSelection() {
        assertTrue(TargetSelectApprovalPlanner.shouldGate(true, true, 1));
        assertFalse(TargetSelectApprovalPlanner.shouldGate(false, true, 1));
        assertFalse(TargetSelectApprovalPlanner.shouldGate(true, false, 1));
        assertFalse(TargetSelectApprovalPlanner.shouldGate(true, true, 0));
    }
}
