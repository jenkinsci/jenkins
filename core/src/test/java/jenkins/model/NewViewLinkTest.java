package jenkins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Action;
import hudson.model.View;
import java.util.List;
import org.junit.Test;

public class NewViewLinkTest {

    private NewViewLink newViewLink = new NewViewLink();

    private View view = mock(View.class);

    @Test
    public void getActionsHasPermission() throws Exception {
        when(view.hasPermission(any())).thenReturn(true);

        final List<Action> actions = newViewLink.createFor(view);

        assertEquals(1, actions.size());
        final Action action = actions.get(0);
        assertEquals(Messages.NewViewLink_NewView(), action.getDisplayName());
        assertEquals(NewViewLink.ICON_FILE_NAME, action.getIconFileName());
        assertEquals(NewViewLink.URL_NAME, action.getUrlName());
    }

    @Test
    public void getActionsNoPermission() throws Exception {
        when(view.hasPermission(any())).thenReturn(false);

        final List<Action> actions = newViewLink.createFor(view);

        assertEquals(1, actions.size());
        final Action action = actions.get(0);
        assertNull(action.getDisplayName());
        assertNull(action.getIconFileName());
        assertEquals(NewViewLink.URL_NAME, action.getUrlName());
    }

}