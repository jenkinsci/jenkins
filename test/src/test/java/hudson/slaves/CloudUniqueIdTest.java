package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Label;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for stable Cloud unique IDs used to fix cloudByIndex ambiguity
 * when multiple clouds share the same name.
 */
@WithJenkins
class CloudUniqueIdTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testUniqueIdGeneration() {
        TestCloud cloud = new TestCloud("test-cloud");
        assertNotNull(cloud.getUniqueId());
        assertFalse(cloud.getUniqueId().isEmpty());
    }

    @Test
    void testUniqueIdConsistency() {
        TestCloud cloud = new TestCloud("test-cloud");
        String id1 = cloud.getUniqueId();
        String id2 = cloud.getUniqueId();
        assertEquals(id1, id2);
    }

    @Test
    void testDifferentCloudsHaveDifferentIds() {
        TestCloud cloud1 = new TestCloud("cloud1");
        TestCloud cloud2 = new TestCloud("cloud2");
        assertNotEquals(cloud1.getUniqueId(), cloud2.getUniqueId());
    }

    @Test
    void testDuplicateNameCloudDeletionKeepsCorrectIds() throws Exception {
        TestCloud cloud1 = new TestCloud("same-name");
        TestCloud cloud2 = new TestCloud("same-name");
        TestCloud cloud3 = new TestCloud("same-name");

        String id1 = cloud1.getUniqueId();
        String id2 = cloud2.getUniqueId();
        String id3 = cloud3.getUniqueId();

        j.jenkins.clouds.add(cloud1);
        j.jenkins.clouds.add(cloud2);
        j.jenkins.clouds.add(cloud3);

        j.jenkins.clouds.remove(cloud1);

        // Simulate form submission: create new cloud with updated name but same uniqueId
        TestCloud updatedCloud2 = new TestCloud("updated-name");
        updatedCloud2.setUniqueId(id2);
        j.jenkins.clouds.replace(cloud2, updatedCloud2);

        assertNull(j.jenkins.getCloudById(id1));
        assertEquals(id2, j.jenkins.getCloudById(id2).getUniqueId());
        assertEquals("updated-name", j.jenkins.getCloudById(id2).name);
        assertEquals(id3, j.jenkins.getCloudById(id3).getUniqueId());
    }

    @Test
    void testUniqueIdsDoNotCollide() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            TestCloud cloud = new TestCloud("cloud-" + i);
            assertTrue(ids.add(cloud.getUniqueId()), "Duplicate ID generated");
        }
    }

    @Test
    void testThreadSafeUniqueIdGeneration() throws Exception {
        TestCloud cloud = new TestCloud("threaded-cloud");

        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        Set<String> ids = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    ids.add(cloud.getUniqueId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, ids.size());
    }

    @Test
    void testGetCloudByIdValidation() {
        assertNull(j.jenkins.getCloudById(null));
        assertNull(j.jenkins.getCloudById(""));
        assertNull(j.jenkins.getCloudById("   "));
        assertNull(j.jenkins.getCloudById("missing"));
    }

    @Test
    void testCloudListGetById() {
        TestCloud cloud1 = new TestCloud("cloud1");
        TestCloud cloud2 = new TestCloud("cloud2");

        j.jenkins.clouds.add(cloud1);
        j.jenkins.clouds.add(cloud2);

        assertEquals(cloud1, j.jenkins.clouds.getById(cloud1.getUniqueId()));
        assertEquals(cloud2, j.jenkins.clouds.getById(cloud2.getUniqueId()));
        assertNull(j.jenkins.clouds.getById("missing"));
    }

    @Test
    void testCloudUrlUsesUniqueIdForDuplicateNames() {
        TestCloud cloud1 = new TestCloud("same-name");
        TestCloud cloud2 = new TestCloud("same-name");

        j.jenkins.clouds.add(cloud1);
        j.jenkins.clouds.add(cloud2);

        // Verify the clouds can be retrieved by their unique IDs
        Cloud retrievedCloud1 = j.jenkins.getCloudById(cloud1.getUniqueId());
        Cloud retrievedCloud2 = j.jenkins.getCloudById(cloud2.getUniqueId());

        assertEquals(cloud1, retrievedCloud1);
        assertEquals(cloud2, retrievedCloud2);

        // Verify both clouds have unique IDs even with same name
        assertNotEquals(cloud1.getUniqueId(), cloud2.getUniqueId());

        // Verify that getCloud returns the first one by name
        assertEquals(cloud1, j.jenkins.getCloud("same-name"));

        // But both can be accessed individually by their unique IDs
        assertNotNull(j.jenkins.getCloudById(cloud1.getUniqueId()));
        assertNotNull(j.jenkins.getCloudById(cloud2.getUniqueId()));
    }

    @Test
    void testUniqueIdPersistedAcrossXStreamSerialization() throws Exception {
        TestCloud original = new TestCloud("test-cloud");
        String originalId = original.getUniqueId();

        // Simulate restart: serialize and deserialize
        String xml = j.jenkins.XSTREAM2.toXML(original);
        TestCloud deserialized = (TestCloud) j.jenkins.XSTREAM2.fromXML(xml);

        assertEquals(originalId, deserialized.getUniqueId(),
                "UUID should be preserved across XStream serialization/deserialization");
    }

    @Test
    void testUuidPersistsAcrossRestartAfterMigration() throws Exception {
        // 1. Create a cloud with no UUID (simulating legacy config)
        TestCloud legacyCloud = new TestCloud("legacy-cloud");

        // 2. Serialize (simulating config.xml with no uniqueId)
        String xml = j.jenkins.XSTREAM2.toXML(legacyCloud);
        assertTrue(xml.contains("<name>legacy-cloud</name>"));

        // 3. Deserialize (triggers readResolve, assigns UUID)
        TestCloud afterFirstBoot = (TestCloud) j.jenkins.XSTREAM2.fromXML(xml);
        String assignedUuid = afterFirstBoot.getUniqueId();
        assertNotNull(assignedUuid, "readResolve should assign a UUID");

        // 4. Serialize again (now WITH the UUID)
        String xmlWithUuid = j.jenkins.XSTREAM2.toXML(afterFirstBoot);
        assertTrue(xmlWithUuid.contains(assignedUuid), "UUID should be in serialized form");

        // 5. Deserialize again (simulating second restart)
        TestCloud afterSecondBoot = (TestCloud) j.jenkins.XSTREAM2.fromXML(xmlWithUuid);

        // 6. UUID should be the same
        assertEquals(assignedUuid, afterSecondBoot.getUniqueId(), "UUID should persist across restarts after migration");
    }

    @Test
    void testProvisionNewIdGeneratesNewUuid() {
        TestCloud cloud = new TestCloud("test-cloud");
        String firstId = cloud.getUniqueId();
        assertNotNull(firstId, "getUniqueId() should generate an ID");

        // provisionNewId() always generates a new ID
        cloud.provisionNewId();
        String secondId = cloud.getUniqueId();

        assertNotEquals(firstId, secondId, "provisionNewId() should generate a new UUID");
    }

    /**
     * Minimal Cloud implementation for testing.
     */
    static class TestCloud extends Cloud {

        TestCloud(String name) {
            super(name);
        }

        @Override
        public Collection<hudson.slaves.NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            return Collections.emptyList();
        }

        @Override
        public boolean canProvision(Label label) {
            return false;
        }

    }
}
