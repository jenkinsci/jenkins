package hudson.cli;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import hudson.model.TopLevelItem;
import hudson.model.ViewGroup;
import hudson.model.View;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import jenkins.model.Jenkins;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Jenkins.class)
@SuppressStaticInitializationFor("hudson.cli.CLICommand")
public class ListJobsCommandTest {

    private /*final*/ Jenkins jenkins;
    private /*final*/ ListJobsCommand command;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Before
    public void setUp() {

        jenkins = mock(Jenkins.class);
        mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        command = mock(ListJobsCommand.class, Mockito.CALLS_REAL_METHODS);
        command.stdout = new PrintStream(stdout);
        command.stderr = new PrintStream(stderr);
    }

    @Test
    public void getNullForNonexistingName() throws Exception {

        when(jenkins.getView(null)).thenReturn(null);
        when(jenkins.getItemByFullName(null)).thenReturn(null);

        // TODO: One would expect -1 here
        assertThat(runWith("NoSuchViewOrItemGroup"), equalTo(0));
        assertThat(stdout, is(empty()));
        assertThat(stderr, is(not(empty())));
    }

    @Test
    public void getAllJobsForEmptyName() throws Exception {

        final Collection<TopLevelItem> jenkinsJobs = Arrays.asList(
                job("some-job"), job("some-other-job")
        );

        when(jenkins.getItems()).thenReturn((List<TopLevelItem>) jenkinsJobs);

        assertThat(runWith(null), equalTo(0));
        assertThat(stderr, is(empty()));
        assertThat(stdout, listsJobs("some-job", "some-other-job"));
    }

    @Test
    public void getJobsFromView() throws Exception {

        final Collection<TopLevelItem> viewJobs = Arrays.asList(
                job("some-job"), job("some-other-job")
        );

        final View customView = mock(View.class);
        when(customView.getItems()).thenReturn(viewJobs);

        when(jenkins.getView("CustomView")).thenReturn(customView);

        assertThat(runWith("CustomView"), equalTo(0));
        assertThat(stderr, is(empty()));
        assertThat(stdout, listsJobs("some-job", "some-other-job"));
    }

    private TopLevelItem job(final String name) {

        final TopLevelItem item = mock(TopLevelItem.class);

        when(item.getDisplayName()).thenReturn(name);

        return item;
    }

    private int runWith(final String name) throws Exception {

        command.name = name;

        return command.run();
    }

    private <T> BaseMatcher<ByteArrayOutputStream> empty() {

        return new BaseMatcher<ByteArrayOutputStream>() {

            @Override
            public boolean matches(Object item) {

                if (!(item instanceof ByteArrayOutputStream)) throw new IllegalArgumentException();

                return ((ByteArrayOutputStream) item).toString().isEmpty();
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Empty output");
            }
        };
    }

    private BaseMatcher<ByteArrayOutputStream> listsJobs(final String... expected) {

        return new BaseMatcher<ByteArrayOutputStream>() {

            @Override
            public boolean matches(Object item) {

                if (!(item instanceof ByteArrayOutputStream)) return false;

                final ByteArrayOutputStream actual = (ByteArrayOutputStream) item;

                final HashSet<String> jobs = new HashSet<String>(
                        Arrays.asList(actual.toString().split("\n"))
                );

                return new HashSet<String>(Arrays.asList(expected)).equals(jobs);
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Job listing of " + Arrays.toString(expected));
            }
        };
    }

    private abstract static class CompositeView extends View implements ViewGroup {

        protected CompositeView(String name) {
            super(name);
        }
    }
}
