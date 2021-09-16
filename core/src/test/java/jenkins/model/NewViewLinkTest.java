package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Action;
import hudson.model.ModifiableViewGroup;
import hudson.model.View;
import hudson.model.ViewGroup;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class NewViewLinkTest {
	
    private NewViewLink newViewLink;

    private View view = mock(View.class);
    private ViewGroup group = mock(ModifiableViewGroup.class);
    private final String viewGroupURL = "abc/";

    @Before
    public void initTests() throws Exception {
        when(view.getOwner()).thenReturn(group);
        when(group.getUrl()).thenReturn(viewGroupURL);

        newViewLink = new NewViewLink();
    }

    @Test
    public void getActionsHasPermission() throws Exception {
        when(group.hasPermission(any())).thenReturn(true);

        final List<Action> actions = newViewLink.createFor(view);

        assertEquals(1, actions.size());
        final Action action = actions.get(0);
        assertEquals(Messages.NewViewLink_NewView(), action.getDisplayName());
        assertEquals(NewViewLink.ICON_FILE_NAME, action.getIconFileName());
        assertEquals("/" + viewGroupURL + NewViewLink.URL_NAME, action.getUrlName());
    }

    @Test
    public void getActionsNoPermission() throws Exception {
        when(group.hasPermission(any())).thenReturn(false);

        final List<Action> actions = newViewLink.createFor(view);

        assertEquals(1, actions.size());
        final Action action = actions.get(0);
        assertNull(action.getIconFileName());
        assertEquals("/" + viewGroupURL + NewViewLink.URL_NAME, action.getUrlName());
    }

    @Test
    public void getActionsNotModifiableOwner() throws Exception {
        ViewGroup vg = mock(ViewGroup.class);
        when(view.getOwner()).thenReturn(vg);
        when(vg.hasPermission(any())).thenReturn(true);

        final List<Action> actions = newViewLink.createFor(view);
        assertThat(actions, hasSize(0));
    }

}
