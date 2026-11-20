/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.XmlFile;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.util.xml.XMLUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** An item's identity: derived from its name until pinned, then kept through renames, updates and restarts. */
@WithJenkins
class ItemUuidTest {

    private static final String POSTED = "<?xml version='1.1' encoding='UTF-8'?>\n<project>\n  <description/>\n  <!-- kept -->\n</project>";

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
        Saves.items.clear();
    }

    @AfterEach
    void tearDown() {
        ItemUuids.PIN_NEW_ITEMS = false;
    }

    private static String config(Item item) throws Exception {
        return Items.getConfigFile(item).asString();
    }

    private static String derived(String fullName) {
        return UUID.nameUUIDFromBytes(("jenkins-item:" + fullName).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Test
    void unpinnedItemsSaveNothingNewAndDeriveTheirIdentityFromTheirName() throws Exception {
        assertFalse(ItemUuids.pinsNewItems());
        MockFolder f = r.createFolder("f");
        FreeStyleProject p = f.createProject(FreeStyleProject.class, "p");
        assertEquals(derived("f/p"), p.getItemUuid());
        assertEquals(derived("f"), f.getItemUuid());
        assertSame(p, ItemUuids.get(p.getItemUuid()));
        p.setDescription("saved");
        p.updateByXml(new StreamSource(new ByteArrayInputStream("<project><description>posted</description></project>".getBytes(StandardCharsets.UTF_8))));
        assertThat(config(p), not(containsString("itemUuid")));
        assertThat(config(f), not(containsString("itemUuid")));

        String before = p.getItemUuid();
        p.renameTo("q");
        assertEquals(derived("f/q"), p.getItemUuid(), "not kept through a rename until pinned");
        assertNull(ItemUuids.get(before));
        assertSame(p, ItemUuids.get(p.getItemUuid()));
        MockFolder g = r.createFolder("g");
        Items.move(f, g); // and the items in a folder moved
        assertEquals(derived("g/f/q"), p.getItemUuid());
        assertNull(ItemUuids.get(derived("f/q")));
        assertSame(p, ItemUuids.get(derived("g/f/q")));
        assertThat(config(p), not(containsString("itemUuid")));
        assertThat(config(f), not(containsString("itemUuid")));
    }

    @Test
    void unpinnedCreationFromXmlAndCopiesWriteWhatTheyDidBefore() throws Exception {
        TopLevelItem created = r.jenkins.createProjectFromXML("saved-posted", new ByteArrayInputStream(POSTED.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        XMLUtils.safeTransform(new StreamSource(new ByteArrayInputStream(POSTED.getBytes(StandardCharsets.UTF_8))), new StreamResult(expected));
        assertArrayEquals(expected.toByteArray(), Files.readAllBytes(Items.getConfigFile(created).getFile().toPath()));

        FreeStyleProject src = r.createFreeStyleProject("src");
        src.setDescription("source");
        Saves.items.clear();
        TopLevelItem copy = r.jenkins.copy((TopLevelItem) src, "saved-copy");
        // saved when made (before the configuration is copied), then by DisplayNameListener: as without identities
        assertEquals(List.of("saved-copy", "saved-copy"), Saves.items);
        assertThat(config(copy), not(containsString("itemUuid")));
        assertThat(config(copy), containsString("<description>source</description>"));
        assertEquals(derived("saved-copy"), copy.getItemUuid());
    }

    @Test
    void pinningGivesAnIdentityOfItsOwnKeptThroughRenamesMovesAndRestarts() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        Saves.items.clear();
        String id = ItemUuids.pin(p);
        assertNotEquals(derived("a"), id, "not the one derived from its name, which a later item of that name would have");
        assertEquals(id, p.getItemUuid());
        assertEquals(id, ItemUuids.pin(p));
        assertEquals(List.of("a"), Saves.items, "saved once");
        assertThat(config(p), containsString("<itemUuid>" + id + "</itemUuid>"));

        p.renameTo("b");
        MockFolder dest = r.createFolder("dest");
        Items.move(p, dest);
        assertEquals(id, p.getItemUuid());
        assertSame(p, ItemUuids.get(id));
        p.updateByXml(new StreamSource(new ByteArrayInputStream("<project><description>posted</description></project>".getBytes(StandardCharsets.UTF_8))));
        assertEquals(id, p.getItemUuid(), "kept through configuration posted without it");
        r.jenkins.reload();
        Item reloaded = r.jenkins.getItemByFullName("dest/b");
        assertEquals(id, reloaded.getItemUuid(), "kept in its configuration");
        assertSame(reloaded, ItemUuids.get(id));
    }

    @Test
    void anotherItemMadeWithThePinnedItemsOldNameGetsAnIdentityOfItsOwn() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = ItemUuids.pin(p);
        p.renameTo("b");
        FreeStyleProject again = r.createFreeStyleProject("a");
        assertNotEquals(id, again.getItemUuid());
        assertSame(p, ItemUuids.get(id));
        assertSame(again, ItemUuids.get(again.getItemUuid()));
        r.jenkins.reload();
        assertNotEquals(r.jenkins.getItemByFullName("a").getItemUuid(), r.jenkins.getItemByFullName("b").getItemUuid());
    }

    @Test
    void copiesOfAPinnedItemDoNotTakeItsIdentity() throws Exception {
        FreeStyleProject src = r.createFreeStyleProject("src");
        String id = ItemUuids.pin(src);
        TopLevelItem copy = r.jenkins.copy((TopLevelItem) src, "copy");
        assertEquals(derived("copy"), copy.getItemUuid());
        assertThat(config(copy), not(containsString("itemUuid")));
        assertSame(src, ItemUuids.get(id));
    }

    @Test
    void newItemsArePinnedWhenAsked() throws Exception {
        ItemUuids.PIN_NEW_ITEMS = true;
        FreeStyleProject p = r.createFreeStyleProject("before");
        String id = p.getItemUuid();
        assertNotNull(UUID.fromString(id));
        assertNotEquals(derived("before"), id);
        assertTrue(p.isItemUuidPinned());
        assertThat(config(p), containsString("<itemUuid>" + id + "</itemUuid>"));
        p.renameTo("after");
        assertEquals(id, p.getItemUuid());
        assertSame(p, ItemUuids.get(id));
        p.updateByXml(new StreamSource(new ByteArrayInputStream("<project><description>posted</description></project>".getBytes(StandardCharsets.UTF_8))));
        assertEquals("posted", p.getDescription());
        assertEquals(id, p.getItemUuid());
        r.jenkins.reload();
        assertEquals(id, r.jenkins.getItemByFullName("after").getItemUuid(), "kept in its configuration");
    }

    @Test
    void pinnedCopiesAndImportsGetTheirOwn() throws Exception {
        ItemUuids.PIN_NEW_ITEMS = true;
        FreeStyleProject src = r.createFreeStyleProject("src");
        TopLevelItem copy = r.jenkins.copy((TopLevelItem) src, "copy");
        assertNotEquals(src.getItemUuid(), copy.getItemUuid());
        assertThat(config(copy), containsString("<itemUuid>" + copy.getItemUuid() + "</itemUuid>"));
        assertThat(config(copy), not(containsString(src.getItemUuid())));
        // the source's configuration posted as a new job
        TopLevelItem imported = r.jenkins.createProjectFromXML("imported", new ByteArrayInputStream(config(src).getBytes(StandardCharsets.UTF_8)));
        assertNotEquals(src.getItemUuid(), imported.getItemUuid());
        assertSame(src, ItemUuids.get(src.getItemUuid()));
        assertSame(imported, ItemUuids.get(imported.getItemUuid()));
        // nor keeps an identity not known here: another instance may have been given the same configuration
        String unknown = UUID.randomUUID().toString();
        TopLevelItem posted = r.jenkins.createProjectFromXML("posted",
                new ByteArrayInputStream(("<project><itemUuid>" + unknown + "</itemUuid></project>").getBytes(StandardCharsets.UTF_8)));
        assertNotEquals(unknown, posted.getItemUuid());
        assertThat(config(posted), not(containsString(unknown)));
        assertSame(posted, ItemUuids.get(posted.getItemUuid()));
    }

    @Test
    void aPinnedItemCreatedFromXmlOrCopiedIsSavedWithAnIdentityOfItsOwn() throws Exception {
        ItemUuids.PIN_NEW_ITEMS = true;
        FreeStyleProject src = r.createFreeStyleProject("src");
        String posted = POSTED.replace("<project>\n", "<project>\n  <itemUuid>" + src.getItemUuid() + "</itemUuid>\n");
        Saves.items.clear();
        TopLevelItem created = r.jenkins.createProjectFromXML("saved-posted", new ByteArrayInputStream(posted.getBytes(StandardCharsets.UTF_8)));
        TopLevelItem copy = r.jenkins.copy((TopLevelItem) src, "saved-copy");
        // once more than unpinned items, with the new identity: the posted one then; a copy when made, then, and by
        // DisplayNameListener
        assertEquals(List.of("saved-posted", "saved-copy", "saved-copy", "saved-copy"), Saves.items);
        String stored = config(created);
        assertThat(stored, containsString("<itemUuid>" + created.getItemUuid() + "</itemUuid>"));
        assertThat(stored, not(containsString(src.getItemUuid())));
        assertThat(config(copy), containsString("<itemUuid>" + copy.getItemUuid() + "</itemUuid>"));
        assertThat(config(copy), not(containsString(src.getItemUuid())));
        r.jenkins.reload();
        assertEquals(created.getItemUuid(), r.jenkins.getItemByFullName("saved-posted").getItemUuid());
        assertEquals(copy.getItemUuid(), r.jenkins.getItemByFullName("saved-copy").getItemUuid());
    }

    @TestExtension
    public static final class Saves extends SaveableListener {
        static final List<String> items = new CopyOnWriteArrayList<>();

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Item i && !i.getName().equals("src")) {
                items.add(i.getName());
            }
        }
    }

    @Test
    void theIdentityOfAnItemItsCreatorCannotSeeIsNotTaken() throws Exception {
        FreeStyleProject secret = r.createFreeStyleProject("secret");
        String id = ItemUuids.pin(secret);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.CREATE).everywhere().to("alice")
                .grant(Item.READ, Item.CONFIGURE).onPaths("mine").to("alice"));
        byte[] xml = ("<project><itemUuid>" + id + "</itemUuid></project>").getBytes(StandardCharsets.UTF_8);
        FreeStyleProject mine;
        try (ACLContext ctx = ACL.as2(User.getById("alice", true).impersonate2())) {
            mine = (FreeStyleProject) r.jenkins.createProjectFromXML("mine", new ByteArrayInputStream(xml));
            assertNull(ItemUuids.get(id), "not visible to alice");
        }
        assertNotEquals(id, mine.getItemUuid(), "one of its own");
        assertSame(secret, ItemUuids.get(id));
        // nor by posting it as the configuration of a job of theirs
        try (ACLContext ctx = ACL.as2(User.getById("alice", true).impersonate2())) {
            mine.updateByXml(new StreamSource(new ByteArrayInputStream(xml)));
        }
        assertNotEquals(id, mine.getItemUuid());
        assertSame(secret, ItemUuids.get(id));
        assertSame(mine, ItemUuids.get(mine.getItemUuid()));
    }

    @Test
    void aDeletedItemIsNotFound() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        String id = ItemUuids.pin(p);
        p.delete();
        assertNull(ItemUuids.get(id));
    }

    @Test
    void anItemThatMayBeDiscoveredButNotReadIsNotFound() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        String id = ItemUuids.pin(p);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.DISCOVER).everywhere().to("bob"));
        try (ACLContext ctx = ACL.as2(User.getById("bob", true).impersonate2())) {
            assertNull(ItemUuids.get(id));
        }
        assertSame(p, ItemUuids.get(id));
    }

    @Test
    void anItemPinnedWhenMadeWithThePinnedItemsOldNameGetsAnIdentityOfItsOwn() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = ItemUuids.pin(p);
        p.renameTo("b");
        PinsWhenCreated.enabled = true;
        FreeStyleProject again;
        try {
            again = r.createFreeStyleProject("a"); // pinned by another listener, before or after the index sees it
        } finally {
            PinsWhenCreated.enabled = false;
        }
        assertTrue(again.isItemUuidPinned());
        assertNotEquals(id, again.getItemUuid());
        assertSame(p, ItemUuids.get(id));
        assertSame(again, ItemUuids.get(again.getItemUuid()));
    }

    @TestExtension("anItemPinnedWhenMadeWithThePinnedItemsOldNameGetsAnIdentityOfItsOwn")
    public static final class PinsWhenCreated extends ItemListener {
        static volatile boolean enabled;

        @Override
        public void onCreated(Item item) {
            if (enabled) {
                try {
                    ItemUuids.pin(item);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    @Test
    void anItemMadeWithTheOldNameWhileARenameIsAnnouncedDoesNotTakeTheIdentity() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = ItemUuids.pin(p);
        CreatesOnRename.enabled = true;
        try {
            p.renameTo("b"); // "a" made again after the rename, before or after the index is told of it
        } finally {
            CreatesOnRename.enabled = false;
        }
        Item again = r.jenkins.getItem("a");
        assertNotNull(again);
        assertEquals(id, p.getItemUuid(), "the pinned item keeps its identity");
        assertNotEquals(id, again.getItemUuid());
        assertSame(p, ItemUuids.get(id));
        assertSame(again, ItemUuids.get(again.getItemUuid()));
    }

    @TestExtension("anItemMadeWithTheOldNameWhileARenameIsAnnouncedDoesNotTakeTheIdentity")
    public static final class CreatesOnRename extends ItemListener {
        static volatile boolean enabled;

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (enabled) {
                enabled = false;
                try {
                    Jenkins.get().createProject(FreeStyleProject.class, oldFullName);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    @Test
    void aRenameChangingOnlyTheCaseKeepsTheIdentity() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = ItemUuids.pin(p);
        p.renameTo("A");
        assertEquals(id, p.getItemUuid());
        assertSame(p, ItemUuids.get(id));
    }

    @Test
    void reloadsIndexWhatChangedOnDisk() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        String id = ItemUuids.pin(p);
        String changed = UUID.randomUUID().toString();
        Path config = Items.getConfigFile(p).getFile().toPath();
        Files.writeString(config, Files.readString(config).replace(id, changed));
        p.doReload();
        assertEquals(changed, p.getItemUuid());
        assertSame(p, ItemUuids.get(changed));
        assertNull(ItemUuids.get(id));

        // a job added on disk, and a whole reload
        String added = UUID.randomUUID().toString();
        Path dir = r.jenkins.getRootDir().toPath().resolve("jobs/added");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.xml"), "<project><itemUuid>" + added + "</itemUuid></project>");
        r.jenkins.reload();
        Item loaded = ItemUuids.get(added);
        assertNotNull(loaded);
        assertSame(r.jenkins.getItem("added"), loaded);
        assertSame(r.jenkins.getItem("p"), ItemUuids.get(changed));
    }

    @Test
    void ofTwoItemsLoadedWithTheSameIdentityOneGetsItsOwn() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = ItemUuids.pin(p);
        // its directory copied while Jenkins is down, then loaded
        FileUtils.copyDirectory(p.getRootDir(), r.jenkins.getRootDir().toPath().resolve("jobs/c").toFile());
        r.jenkins.reload();
        Item a = r.jenkins.getItem("a");
        Item c = r.jenkins.getItem("c");
        assertEquals(id, a.getItemUuid(), "the first keeps it");
        assertNotEquals(id, c.getItemUuid());
        assertSame(a, ItemUuids.get(id));
        assertSame(c, ItemUuids.get(c.getItemUuid()));
        assertThat(config(c), containsString("<itemUuid>" + c.getItemUuid() + "</itemUuid>"));
    }

    @Test
    void twoItemsPostedTheSameNewIdentityAtOnceDoNotBothKeepIt() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        FreeStyleProject q = r.createFreeStyleProject("q");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 20; i++) {
                byte[] xml = ("<project><itemUuid>" + UUID.randomUUID() + "</itemUuid></project>").getBytes(StandardCharsets.UTF_8);
                CyclicBarrier together = new CyclicBarrier(2);
                List<Future<?>> posts = new ArrayList<>();
                for (FreeStyleProject j : List.of(p, q)) {
                    posts.add(pool.submit(() -> {
                        together.await();
                        j.updateByXml(new StreamSource(new ByteArrayInputStream(xml)));
                        return null;
                    }));
                }
                for (Future<?> f : posts) {
                    f.get();
                }
                assertNotEquals(p.getItemUuid(), q.getItemUuid());
                assertSame(p, ItemUuids.get(p.getItemUuid()));
                assertSame(q, ItemUuids.get(q.getItemUuid()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pinningAnItemWithTheIdentityOfAnItemItsPinnerCannotSeeGivesItItsOwn() throws Exception {
        FreeStyleProject hidden = r.createFreeStyleProject("hidden");
        String id = ItemUuids.pin(hidden);
        FreeStyleProject mine = r.createFreeStyleProject("mine");
        mine.setItemUuid(id); // as configuration posted with it, before the index has checked it
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.DISCOVER).everywhere().to("alice")
                .grant(Item.READ, Item.CONFIGURE).onPaths("mine").to("alice"));
        String pinned;
        try (ACLContext ctx = ACL.as2(User.getById("alice", true).impersonate2())) {
            pinned = ItemUuids.pin(mine); // alice may discover "hidden", not read it
        }
        assertNotEquals(id, pinned);
        assertEquals(pinned, mine.getItemUuid());
        assertThat(config(mine), containsString("<itemUuid>" + pinned + "</itemUuid>"));
        assertSame(hidden, ItemUuids.get(id));
        assertSame(mine, ItemUuids.get(pinned));
    }

    @Test
    void anIdentityThatIsNotAUuidIsNotKept() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        String id = ItemUuids.pin(p);
        String other = ItemUuids.pin(r.createFreeStyleProject("other"));
        FreeStyleProject unpinned = r.createFreeStyleProject("unpinned");
        for (String posted : List.of(other + "/builds", "../" + other, "not-a-uuid", "")) {
            byte[] xml = ("<project><itemUuid>" + posted + "</itemUuid></project>").getBytes(StandardCharsets.UTF_8);
            p.updateByXml(new StreamSource(new ByteArrayInputStream(xml)));
            assertEquals(id, p.getItemUuid(), posted);
            assertThat(config(p), containsString("<itemUuid>" + id + "</itemUuid>"));
            unpinned.updateByXml(new StreamSource(new ByteArrayInputStream(xml)));
            assertFalse(unpinned.isItemUuidPinned(), posted);
            assertEquals(derived("unpinned"), unpinned.getItemUuid());
        }
        assertSame(p, ItemUuids.get(id));

        // one in another form is kept in the usual one
        String upper = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        p.updateByXml(new StreamSource(new ByteArrayInputStream(("<project><itemUuid>" + upper + "</itemUuid></project>").getBytes(StandardCharsets.UTF_8))));
        assertEquals(upper.toLowerCase(Locale.ROOT), p.getItemUuid());
        assertSame(p, ItemUuids.get(upper.toLowerCase(Locale.ROOT)));

        // nor is one on disk
        Path dir = r.jenkins.getRootDir().toPath().resolve("jobs/added");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.xml"), "<project><itemUuid>" + other + "/builds</itemUuid></project>");
        r.jenkins.reload();
        FreeStyleProject added = (FreeStyleProject) r.jenkins.getItem("added");
        assertNotNull(added, "loaded all the same");
        assertFalse(added.isItemUuidPinned());
        assertEquals(derived("added"), added.getItemUuid());
        assertThrows(IllegalArgumentException.class, () -> added.setItemUuid(other + "/builds"));
    }

    @Test
    void anItemDeletedAndMadeAgainIsPinnedWithANewIdentity() throws Exception {
        String first = ItemUuids.pin(r.createFreeStyleProject("a"));
        r.jenkins.getItem("a").delete();
        String second = ItemUuids.pin(r.createFreeStyleProject("a"));
        assertNotEquals(first, second);
        assertNull(ItemUuids.get(first));
    }

    @Test
    void aPinMadeWhileConfigurationIsPostedIsKept() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 30; i++) {
                FreeStyleProject p = r.createFreeStyleProject("p" + i);
                CyclicBarrier together = new CyclicBarrier(2);
                Future<String> pin = pool.submit(() -> {
                    together.await();
                    return ItemUuids.pin(p);
                });
                Future<?> post = pool.submit(() -> {
                    together.await();
                    p.updateByXml(new StreamSource(new ByteArrayInputStream("<project><description>posted</description></project>".getBytes(StandardCharsets.UTF_8))));
                    return null;
                });
                post.get();
                String id = pin.get();
                assertTrue(p.isItemUuidPinned());
                assertEquals(id, p.getItemUuid());
                assertThat(config(p), containsString("<itemUuid>" + id + "</itemUuid>"));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
