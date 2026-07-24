package crossspire.map;

/** Stable coordinate identities shared by captured and reconstructed STS map nodes. */
public final class StsMapTopology {

    private StsMapTopology() {}

    public static String nodeId(int x, int y) {
        return x + ":" + y;
    }
}
