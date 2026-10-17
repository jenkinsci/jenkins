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
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.XmlFile;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.util.xml.XMLUtils;
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
    void pinningKeepsTheDerivedIdentityThroughRenamesMovesAndRestarts() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("a");
        String id = p.getItemUuid();
        Saves.items.clear();
        assertEquals(id, ItemUuids.pin(p), "the identity it had, so data already kept under it stays its");
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
}
