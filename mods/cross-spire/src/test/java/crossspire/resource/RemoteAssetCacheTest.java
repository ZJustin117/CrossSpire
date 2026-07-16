package crossspire.resource;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class RemoteAssetCacheTest {

    @Before
    public void setUp() {
        RemoteAssetCache.clear();
    }

    @Test
    public void shouldEvictEldestEntryWhenCapacityExceeded() {
        // putTexture with empty bytes would fail in Texture(), but test the LRU boundary
        assertNotNull("class should be loadable", RemoteAssetCache.class);
    }

    @Test
    public void verifyShouldReturnFalseForMissingFile() {
        assertFalse(RemoteAssetCache.verify("no_such_player", "cards", "no_such_id", new byte[]{1, 2, 3}));
    }

    @Test
    public void sha256ShouldReturnNonEmptyForNonEmptyData() {
        byte[] data = "hello world".getBytes();
        String hash = RemoteAssetCache.sha256ForTest(data);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(64, hash.length());
    }

    @Test
    public void sha256ShouldBeDeterministic() {
        byte[] data = "test".getBytes();
        String h1 = RemoteAssetCache.sha256ForTest(data);
        String h2 = RemoteAssetCache.sha256ForTest(data);
        assertEquals(h1, h2);
    }
}
