package hudson.model;

import hudson.security.ACL;
import hudson.security.Permission;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import hudson.views.ListViewColumn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Ignore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest(Jenkins.class)
@RunWith(PowerMockRunner.class)
public class ListViewTest {
    
    private interface ItemGroupOfNonTopLevelItem extends TopLevelItem, ItemGroup<Item> {}

    @Ignore("TODO I am not smart enough to figure out what PowerMock is actually doing; whatever this was testing, better move to the test module and use JenkinsRule")
    @Test
    @PrepareForTest({ListViewColumn.class,Items.class})
    public void listItemRecurseWorksWithNonTopLevelItems() throws IOException{
        mockStatic(Items.class);
        mockStatic(ListViewColumn.class);
        List<ListViewColumn> columns = Collections.emptyList();
        when(ListViewColumn.createDefaultInitialColumnList(ListView.class)).thenReturn(columns);
        ViewGroup owner = mock(ViewGroup.class);
        ItemGroup itemGroupOwner = mock(ItemGroup.class);
        when(owner.getItemGroup()).thenReturn(itemGroupOwner);
        ListView lv = new ListView("test", owner);
        ItemGroupOfNonTopLevelItem ig = Mockito.mock(ItemGroupOfNonTopLevelItem.class);
        when(Items.getAllItems(eq(itemGroupOwner), eq(TopLevelItem.class))).thenReturn(Arrays.asList((TopLevelItem) ig));
        when(ig.getRelativeNameFrom(any(ItemGroup.class))).thenReturn("test-item");
        lv.setRecurse(true);
        lv.add(ig);
        assertEquals(1, lv.getItems().size());
    }
    
    @Test
    @PrepareForTest({ListViewColumn.class,Items.class})
    public void includeRegexProgrammatic() {
        mockStatic(Items.class);
        mockStatic(ListViewColumn.class);
        List<ListViewColumn> columns = Collections.emptyList();
        when(ListViewColumn.createDefaultInitialColumnList(ListView.class)).thenReturn(columns);
        ViewGroup owner = mock(ViewGroup.class);
        ItemGroup ig = mock(ItemGroup.class);
        when(owner.getItemGroup()).thenReturn(ig);
        ListView view = new ListView("test", owner);
        view.setIncludeRegex(".*");
        TopLevelItem it = Mockito.mock(TopLevelItem.class);
        List<TopLevelItem> igContent = Arrays.asList((TopLevelItem) it);
        when(Items.getAllItems(eq(ig), eq(TopLevelItem.class))).thenReturn(igContent);
        when(ig.getItems()).thenReturn(igContent);
        when(it.getRelativeNameFrom(any(ItemGroup.class))).thenReturn("test-item");
        assertEquals(1, view.getItems().size());
    }
    
    @Test
    @Issue("JENKINS-23411")
    @PrepareForTest({ListViewColumn.class,Items.class})
    public void doRemoveJobFromView_missing_item() throws IOException, ServletException {
        final String NON_EXISTENT_ITEM="non_existent_project";
        mockStatic(Items.class);
        mockStatic(ListViewColumn.class);
        List<ListViewColumn> columns = Collections.emptyList();
        when(ListViewColumn.createDefaultInitialColumnList()).thenReturn(columns);
        ViewGroup owner = mock(ViewGroup.class);
        ItemGroup ig = mock(ItemGroup.class);
        when(owner.getItemGroup()).thenReturn(ig);
          
        PowerMockito.mockStatic(Jenkins.class);
        Jenkins j = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(j.getItemByFullName(NON_EXISTENT_ITEM, TopLevelItem.class)).thenReturn(null);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(j);
        
        ListView lv = mock(ListView.class);
        when(lv.getACL()).thenReturn(new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                return true;
            }
        });
        when(lv.doRemoveJobFromView(NON_EXISTENT_ITEM)).thenCallRealMethod();
        when(lv.getOwnerItemGroup()).thenCallRealMethod();
        lv.name="test";
        lv.initColumns();
        lv.initJobFilters();
        lv.owner=owner;
        lv.setIncludeRegex(".*");
        try {
            lv.doRemoveJobFromView(NON_EXISTENT_ITEM);
        } catch (Failure failure) {      
            assertTrue("Expected that method fails to discover the item",failure.getMessage().startsWith("Cannot discover the item by name"));
            return;
        }
        fail("Expected that method fails to discover the item");
    }
}
