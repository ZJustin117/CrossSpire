package crossspire.rng;

import java.util.Random;

public class SyncedRng {

    private final long seed;
    private final Random random;

    public SyncedRng(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public long getSeed() {
        return seed;
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
