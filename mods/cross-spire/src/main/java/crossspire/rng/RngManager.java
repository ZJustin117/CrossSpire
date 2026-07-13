package crossspire.rng;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RngManager {

    private long seed;
    private final Map<String, SyncedRng> streams = new ConcurrentHashMap<>();

    public RngManager(long seed) {
        this.seed = seed;
    }

    public SyncedRng get(String name) {
        streams.computeIfAbsent(name, k -> {
            long derivedSeed = seed ^ name.hashCode();
            return new SyncedRng(derivedSeed);
        });
        return streams.get(name);
    }

    public void reseed(long newSeed) {
        this.seed = newSeed;
        streams.clear();
    }
}
