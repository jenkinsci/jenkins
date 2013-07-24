package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import hudson.views.ListViewColumn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class ListViewTest {
    
    private interface ItemGroupOfNonTopLevelItem extends TopLevelItem, ItemGroup<Item> {}

    @Test
    @PrepareForTest({ListViewColumn.class,Items.class})
    public void listItemRecurseWorksWithNonTopLevelItems() throws IOException{
        mockStatic(Items.class);
        mockStatic(ListViewColumn.class);
        List<ListViewColumn> columns = Collections.emptyList();
        when(ListViewColumn.createDefaultInitialColumnList()).thenReturn(columns);
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
        when(ListViewColumn.createDefaultInitialColumnList()).thenReturn(columns);
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
}
