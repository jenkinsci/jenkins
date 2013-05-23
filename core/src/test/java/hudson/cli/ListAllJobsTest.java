package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.model.TopLevelItem;
import hudson.model.ViewGroup;
import hudson.model.View;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import jenkins.model.Jenkins;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ListAllJobsTest {

    @Mock private Jenkins jenkins;
    private final ListJobsCommand command = new ListJobsCommand();

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getAllJobsForEmptyName() {

        final Collection<TopLevelItem> jenkinsJobs = Arrays.asList(
                mock(TopLevelItem.class)
        );

        when(jenkins.getItems()).thenReturn((List<TopLevelItem>)jenkinsJobs);

        // Display whatever Jenkins.getItems() returns
        assertThat(getJobsFor(null), equalToInAnyOrder(jenkinsJobs));
    }

    @Test
    public void getJobsFromView() {

        final Collection<TopLevelItem> viewJobs = Arrays.asList(
                mock(TopLevelItem.class)
        );

        final View customView = mock(View.class);
        when(customView.getItems()).thenReturn(viewJobs);

        when(jenkins.getView("CustomView")).thenReturn(customView);

        assertThat(getJobsFor("CustomView"), equalToInAnyOrder(viewJobs));
    }

    @Test
    public void getJobsRecursivelyFromViewGroup() {

        final CompositeView rootView = mock(CompositeView.class);
        final View leftView = mock(View.class);
        final View rightView = mock(View.class);

        final TopLevelItem rootJob = mock(TopLevelItem.class);
        final TopLevelItem leftJob = mock(TopLevelItem.class);
        final TopLevelItem rightJob = mock(TopLevelItem.class);
        final TopLevelItem sharedJob = mock(TopLevelItem.class);

        when(rootView.getViews()).thenReturn(Arrays.asList(leftView, rightView));
        when(rootView.getItems()).thenReturn(Arrays.asList(rootJob, sharedJob));
        when(leftView.getItems()).thenReturn(Arrays.asList(leftJob, sharedJob));
        when(rightView.getItems()).thenReturn(Arrays.asList(rightJob));

        when(jenkins.getView("Root")).thenReturn(rootView);

        final Collection<TopLevelItem> allJobs = Arrays.asList(
                rootJob, sharedJob, leftJob, rightJob
        );

        assertThat(getJobsFor("Root"), equalToInAnyOrder(allJobs));
    }

    private Collection<TopLevelItem> getJobsFor(final String name) {

        command.name = name;

        return command.getJobs(jenkins);
    }

    private <T> BaseMatcher<Collection<T>> equalToInAnyOrder(
            final Collection<T> expected
    ) {

        return new BaseMatcher<Collection<T>>() {

            @Override
            public boolean matches(Object item) {

                if (!(item instanceof Collection)) return false;

                @SuppressWarnings("unchecked")
                final Collection<T> actual = (Collection<T>) item;

                return new HashSet<T>(expected).equals(new HashSet<T>(actual));
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Collection containing in any order " + expected);
            }
        };
    }

    private abstract static class CompositeView extends View implements ViewGroup {

        protected CompositeView(String name) {
            super(name);
        }
    }
}
