package crossspire.event;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for event-gated hand/target selection UI. */
public final class TargetSelectApprovalPlanner {

    private TargetSelectApprovalPlanner() {}

    public static String[] targetIds(List<String> selectedTargetIds) {
        if (selectedTargetIds == null || selectedTargetIds.isEmpty()) return new String[0];
        List<String> out = new ArrayList<String>();
        for (String id : selectedTargetIds) {
            if (id != null && !id.isEmpty()) out.add(id);
        }
        return out.toArray(new String[0]);
    }

    public static boolean shouldGate(boolean eventBound, boolean confirmClicked, int selectedCount) {
        return eventBound && confirmClicked && selectedCount > 0;
    }
}
