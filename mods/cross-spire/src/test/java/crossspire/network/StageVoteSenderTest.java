package crossspire.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class StageVoteSenderTest {

    @Test
    public void shouldBuildStageVoteMessage() {
        String json = StageVoteSender.buildStageVote("alice", "bob");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("stage_vote", obj.get("type").getAsString());
        assertEquals("alice", obj.get("source").getAsString());
        assertEquals("bob", obj.get("candidate").getAsString());
    }

    @Test
    public void shouldBuildStageVotesSnapshot() {
        String json = StageVoteSender.buildStageVotes("host", "{\"alice\":\"bob\",\"bob\":\"bob\"}");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("stage_votes", obj.get("type").getAsString());
        assertEquals("host", obj.get("source").getAsString());
        assertNotNull(obj.get("votes"));
    }

    @Test
    public void shouldBuildStageHostResult() {
        String json = StageVoteSender.buildStageHostResult("host", "bob");
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("stage_host_result", obj.get("type").getAsString());
        assertEquals("host", obj.get("source").getAsString());
        assertEquals("bob", obj.get("host_id").getAsString());
    }
}
