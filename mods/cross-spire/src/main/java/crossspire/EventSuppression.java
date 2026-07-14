package crossspire;

import java.util.concurrent.atomic.AtomicInteger;

public final class EventSuppression {

    static final AtomicInteger SUPPRESSION = new AtomicInteger(0);

    public static void suppressEvents(Runnable fn) {
        SUPPRESSION.incrementAndGet();
        try { fn.run(); }
        finally { SUPPRESSION.decrementAndGet(); }
    }

    public static boolean isSuppressed() {
        return SUPPRESSION.get() > 0;
    }

    private EventSuppression() {}
}
