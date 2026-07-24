package crossspire.event;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for event-gated grid card selection. */
public final class GridCardSelectApprovalPlanner {

    private GridCardSelectApprovalPlanner() {}

    public static String[] cardIds(List<String> selectedCardIds) {
        if (selectedCardIds == null || selectedCardIds.isEmpty()) return new String[0];
        List<String> out = new ArrayList<String>();
        for (String id : selectedCardIds) {
            if (id != null && !id.isEmpty()) out.add(id);
        }
        return out.toArray(new String[0]);
    }

    public static boolean shouldGate(boolean eventBound, boolean confirmClicked, int selectedCount) {
        return eventBound && confirmClicked && selectedCount > 0;
    }
}
