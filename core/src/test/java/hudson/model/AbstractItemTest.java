/**
 * 
 */
package hudson.model;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author kingfai
 *
 */
@SuppressWarnings("unchecked")
public class AbstractItemTest {

    private class StubAbstractItem extends AbstractItem {

        protected StubAbstractItem() {
            // sending in null as parent as I don't care for my current tests
            super(null, "StubAbatractItem");
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Collection<? extends Job> getAllJobs() {
            return null;
        }
        
        /**
         * Override save so that nothig happens when setDisplayName() is called
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
        Assert.assertEquals(displayName, i.getDisplayName());
    }
    
    @Test
    public void testGetDefaultDisplayName() {
        final String name = "the item name";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        // assert that if the displayname is not set, the name is actually returned
        Assert.assertEquals(name,  i.getDisplayName());
        
    }
    
    @Test
    public void testSearchNameIsName() throws Exception {
        final String name = "the item name jlrtlekjtekrjkjr";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(name);
        
        Assert.assertEquals(i.getName(),  i.getSearchName());        
    }
    
    @Test
    public void testGetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        Assert.assertEquals(projectName, i.getName());
        Assert.assertNull(i.getDisplayNameOrNull());
        
        i.setDisplayName(displayName);
        Assert.assertEquals(displayName, i.getDisplayNameOrNull());
    }

    @Test
    public void testSetDisplayNameOrNull() throws Exception {
        final String projectName = "projectName";
        final String displayName = "displayName";
        StubAbstractItem i = new StubAbstractItem();
        i.doSetName(projectName);
        Assert.assertNull(i.getDisplayNameOrNull());

        i.setDisplayNameOrNull(displayName);
        Assert.assertEquals(displayName, i.getDisplayNameOrNull());
        Assert.assertEquals(displayName, i.getDisplayName());
    }
}
