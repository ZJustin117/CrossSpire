package crossspire.event;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridCardSelectApprovalPlannerTest {

    @Test
    public void extractsNonEmptyCardIds() {
        String[] ids = GridCardSelectApprovalPlanner.cardIds(
            Arrays.asList("Strike_R", "", null, "Defend_R"));
        assertEquals(2, ids.length);
        assertEquals("Strike_R", ids[0]);
        assertEquals("Defend_R", ids[1]);
        assertEquals(0, GridCardSelectApprovalPlanner.cardIds(Collections.<String>emptyList()).length);
    }

    @Test
    public void gatesOnlyBoundConfirmWithSelection() {
        assertTrue(GridCardSelectApprovalPlanner.shouldGate(true, true, 1));
        assertFalse(GridCardSelectApprovalPlanner.shouldGate(false, true, 1));
        assertFalse(GridCardSelectApprovalPlanner.shouldGate(true, false, 1));
        assertFalse(GridCardSelectApprovalPlanner.shouldGate(true, true, 0));
    }
}
