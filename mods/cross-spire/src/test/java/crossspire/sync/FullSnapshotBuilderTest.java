package crossspire.sync;

import com.google.gson.JsonObject;
import crossspire.combat.ComponentAttachment;
import crossspire.map.MapDefinition;
import crossspire.map.MapNode;
import crossspire.map.MapRegistry;
import crossspire.party.PartyManager;
import crossspire.party.PartyState;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FullSnapshotBuilderTest {

    @Test
    public void includesPartiesMapsAttachmentsAndActiveNodes() {
        PartyManager parties = new PartyManager();
        parties.initializeDefaultParty(Arrays.asList("alice", "bob"), "0:-1");
        parties.bindMap("P0", "M1", "0:-1", "EXORDIUM");
        parties.setNodeInstanceHost("P0", "alice");
        parties.enterNode("P0", "0:0", "node:M1/P0/0:0/1");
        PartyState party = parties.getParty("P0");

        MapRegistry maps = new MapRegistry();
        maps.register("alice", new MapDefinition("M1", "EXORDIUM", 1, "d", "0:-1",
            Arrays.asList(
                new MapNode("0:-1", Arrays.asList("0:0")),
                new MapNode("0:0", "monster", Collections.<String>emptyList()))));

        ComponentAttachment att = ComponentAttachment.ofPower(
            "att-1", "Vulnerable", "alice", "monster:0", 2);

        JsonObject snap = FullSnapshotBuilder.buildDirectory(
            parties.snapshot(), maps, Collections.singletonList(att));

        assertEquals("full_snapshot", snap.get("type").getAsString());
        assertEquals(1, snap.getAsJsonArray("parties").size());
        assertEquals("P0", snap.getAsJsonArray("parties").get(0).getAsJsonObject()
            .get("party_id").getAsString());
        assertEquals(1, snap.getAsJsonArray("maps").size());
        assertEquals("M1", snap.getAsJsonArray("maps").get(0).getAsJsonObject()
            .get("map_instance_id").getAsString());
        assertEquals(1, snap.getAsJsonArray("attachments").size());
        assertEquals("Vulnerable", snap.getAsJsonArray("attachments").get(0).getAsJsonObject()
            .get("power_id").getAsString());
        assertEquals(1, snap.getAsJsonArray("active_node_instances").size());
        assertEquals("node:M1/P0/0:0/1", snap.getAsJsonArray("active_node_instances").get(0)
            .getAsJsonObject().get("node_instance_id").getAsString());
        assertTrue(party.activeNodeInstanceId.startsWith("node:"));
    }
}
