package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author kingfai
 */
public class AbstractItemTest {

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
    public void testSetDisplayName() throws Exception {
        final String displayName = "testDisplayName";
        StubAbstractItem i = new StubAbstractItem();
        i.setDisplayName(displayName);
        assertEquals(displayName, i.getDisplayName());
    }
    
    @Test
    public void testGetDefaultDisplayName() {
        final String name = "the item name";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        // assert that if the displayname is not set, the name is actually returned
        assertEquals(name,  i.getDisplayName());
        
    }
    
    @Test
    public void testSearchNameIsName() throws Exception {
        final String name = "the item name jlrtlekjtekrjkjr";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        
        assertEquals(i.getName(),  i.getSearchName());
    }
    
    @Test
    public void testGetDisplayNameOrNull() throws Exception {
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
    public void testSetDisplayNameOrNull() throws Exception {
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

        protected NameNotEditableItem(ItemGroup parent, String name){
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
    public void renameMethodShouldThrowExceptionWhenNotIsNameEditable() {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null,"NameNotEditableItem");

        //WHEN
        try {
            item.renameTo("NewName");
            fail("An item with isNameEditable false must throw exception when trying to rename it.");
        } catch (IOException e) {

            //THEN
            assertEquals(e.getMessage(),"Trying to rename an item that does not support this operation.");
            assertEquals("NameNotEditableItem",item.getName());
        }
    }

    @Test
    @Issue("JENKINS-58571")
    public void doConfirmRenameMustThrowFormFailureWhenNotIsNameEditable() throws IOException {

        //GIVEN
        NameNotEditableItem item = new NameNotEditableItem(null,"NameNotEditableItem");

        //WHEN
        try {
            item.doConfirmRename("MyNewName");
            fail("An item with isNameEditable false must throw exception when trying to call doConfirmRename.");
        } catch (Failure f) {

            //THEN
            assertEquals(f.getMessage(),"Trying to rename an item that does not support this operation.");
            assertEquals("NameNotEditableItem",item.getName());
        }
    }

}
