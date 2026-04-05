package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author kingfai
 */
class AbstractItemTest {

    private static class StubAbstractItem extends AbstractItem {

        protected StubAbstractItem() {
            // sending in null as parent as I don't care for my current tests
            super(null, "StubAbstractItem");
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        /**
         * Override save so that nothing happens when setDisplayName() is called
         */
        @Override
        public void save() {

        }
    }

    @Test
    void testSetDisplayName() throws Exception {
        final String displayName = "testDisplayName";
        StubAbstractItem i = new StubAbstractItem();
        i.setDisplayName(displayName);
        assertEquals(displayName, i.getDisplayName());
    }

    @Test
    void testGetDefaultDisplayName() {
        final String name = "the item name";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        // assert that if the displayname is not set, the name is actually returned
        assertEquals(name,  i.getDisplayName());

    }

    @Test
    void testSearchNameIsName() {
        final String name = "the item name jlrtlekjtekrjkjr";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);

        assertEquals(i.getName(),  i.getSearchName());
    }

    @Test
    void testGetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        assertEquals(projectName, i.getName());
        assertNull(i.getDisplayNameOrNull());

        i.setDisplayName(displayName);
        assertEquals(displayName, i.getDisplayNameOrNull());
    }

    @Test
    void testSetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        assertNull(i.getDisplayNameOrNull());

        i.setDisplayNameOrNull(displayName);
        assertEquals(displayName, i.getDisplayNameOrNull());
        assertEquals(displayName, i.getDisplayName());
    }

    private static class NameNotEditableItem extends AbstractItem {

        protected NameNotEditableItem(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }

        @Override
        public boolean isNameEditable() {
            return false; //so far it's the default value, but it's good to be explicit for test.
        }
    }

    @Test
    @Issue("JENKINS-58571")
    void renameMethodShouldThrowExceptionWhenNotIsNameEditable() {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null, "NameNotEditableItem");

        //WHEN
        final IOException e = assertThrows(IOException.class, () -> item.renameTo("NewName"), "An item with isNameEditable false must throw exception when trying to rename it.");

        assertEquals("Trying to rename an item that does not support this operation.", e.getMessage());
        assertEquals("NameNotEditableItem", item.getName());
    }

    @Test
    @Issue("JENKINS-58571")
    void doConfirmRenameMustThrowFormFailureWhenNotIsNameEditable() {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null, "NameNotEditableItem");

        //WHEN
        final Failure f = assertThrows(Failure.class, () -> item.doConfirmRename("MyNewName"), "An item with isNameEditable false must throw exception when trying to call doConfirmRename.");
        assertEquals("Trying to rename an item that does not support this operation.", f.getMessage());
        assertEquals("NameNotEditableItem", item.getName());
    }

}
