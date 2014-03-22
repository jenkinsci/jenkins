package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import hudson.cli.CLICommandInvoker.Result;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.MockViewGroup;

public class ListItemsCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private CLICommandInvoker command;

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, new ListItemsCommand());
    }

    @Test
    public void getAllJobs() throws Exception {

        j.createFreeStyleProject("top_level_project").setDisplayName("Top Level Project");

        ListView view = new ListView("view", j.jenkins);
        view.add(j.createFreeStyleProject("project_in_view"));
        j.jenkins.addView(view);

        MockFolder folder = j.createFolder("folder");
        folder.createProject(FreeStyleProject.class, "project_in_folder");

        Result result = command.invoke();

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("top_level_project", "project_in_view", "folder"));

        result = command.invokeWithArgs("-r");

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("top_level_project", "project_in_view", "folder", "folder/project_in_folder"));
    }

    @Test
    public void failForNonexistingName() {

        Result result = command.invokeWithArgs("NoSuchViewOrItemGroup");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view or item group with the given name found"));
    }

    @Test
    @Bug(18393)
    public void failForMatrixProject() throws Exception {

        MatrixProject project = j.createMatrixProject("MatrixProject");
        project.setAxes(new AxisList(new TextAxis("axis", "a", "b", "c")));

        Result result = command.invokeWithArgs("MatrixProject");

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view or item group with the given name found"));
    }

    @Test
    public void getJobsFromFolderRecursively() throws Exception {

        j.createFreeStyleProject("top_level_project");

        MockFolder folder = j.createFolder("MyFolder");
        folder.createProject(FreeStyleProject.class, "project_in_folder");

        folder.createProject(MockFolder.class, "InnerFolder")
                .createProject(FreeStyleProject.class, "deeply_nested_project")
        ;

        Result result = command.invokeWithArgs("MyFolder");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs(
                "MyFolder/project_in_folder", "MyFolder/InnerFolder"
        ));

        result = command.invokeWithArgs("--recursive", "MyFolder");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs(
                "MyFolder/project_in_folder", "MyFolder/InnerFolder", "MyFolder/InnerFolder/deeply_nested_project"
        ));
    }

    @Test
    public void getJobsFromViewGroup() throws Exception {

        j.createFreeStyleProject("top_level_project");

        MockViewGroup viewGroup = new MockViewGroup("MyViewGroup", j.jenkins);
        j.jenkins.addView(viewGroup);
        viewGroup.getItems().add(j.createFreeStyleProject("project_in_view"));

        ListView innerView = new ListView("InnerView", viewGroup);
        viewGroup.getViews().add(innerView);
        innerView.add(j.createFreeStyleProject("deeply_nested_project"));

        Result result = command.invokeWithArgs("MyViewGroup");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("project_in_view"));

        result = command.invokeWithArgs("--recursive", "MyViewGroup");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("project_in_view", "deeply_nested_project"));
    }

    @Test @SuppressWarnings("deprecation")
    public void listJobsShouldBeDeprecatedInFavourOfListItems() {

        assertTrue(new ListJobsCommand().isDeprecated());

        CLICommandInvoker invoker = new CLICommandInvoker(j, new HelpCommand());

        Result result = invoker.invoke();
        assertThat(result, succeeded());
        assertThat(result.stderr(), containsString("list-items"));
        assertThat(result.stderr(), not(containsString("list-jobs")));

        result = invoker.invokeWithArgs("list-jobs");
        assertThat(result, succeeded());
        assertThat(result.stderr(), containsString("Deprecated: "));
    }

    private TypeSafeMatcher<Result> listsOnlyJobs(final String... expected) {
        final HashSet<String> expectedNames = new HashSet<String>(Arrays.asList(expected));

        return new TypeSafeMatcher<Result>() {

            @Override
            protected boolean matchesSafely(Result result) {
                final Set<String> actualNames = actual(result);

                if (!expectedNames.equals(actualNames)) return false;

                for (String actual: actualNames) {
                    if (j.jenkins.getItemByFullName(actual) == null) return false;
                }

                return true;
            }

            @Override
            public void describeTo(Description description) {

                description.appendText("Job listing of " + expectedNames);
            }

            @Override
            protected void describeMismatchSafely(Result result, Description desc) {
                final Set<String> actualNames = actual(result);

                if (!expectedNames.equals(actualNames)) {
                    desc.appendText(actualNames.toString() + "\n");
                }

                for (String actual: actualNames) {
                    if (j.jenkins.getItemByFullName(actual) == null) {
                        desc.appendText(actual).appendText(" can not be read by Jenkins\n");
                    }
                }
            }

            private Set<String> actual(Result result) {
                return new HashSet<String>(
                        Arrays.asList(result.stdout().split(System.getProperty("line.separator")))
                );
            }
        };
    }
}
