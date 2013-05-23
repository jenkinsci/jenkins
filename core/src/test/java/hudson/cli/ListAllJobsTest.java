package hudson.cli;

import static org.junit.Assert.*;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.model.TopLevelItem;
import hudson.model.View;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;

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

        final List<TopLevelItem> jenkinsJobs = Collections.emptyList();

        when(jenkins.getItems()).thenReturn(jenkinsJobs);

        // Display whatever Jenkins.getItems() returns
        assertSame(jenkinsJobs, getJobsFor(null));
    }

    @Test
    public void getJobsFromView() {

        final List<TopLevelItem> viewJobs = Collections.emptyList();

        final View customView = mock(View.class);
        when(customView.getItems()).thenReturn(viewJobs);

        when(jenkins.getView("CustomView")).thenReturn(customView);

        assertSame(viewJobs, getJobsFor("CustomView"));
    }

    private Collection<TopLevelItem> getJobsFor(final String name) {

        command.name = name;

        return command.getJobs(jenkins);
    }
}
