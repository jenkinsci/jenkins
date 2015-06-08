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

    private CLICommandInvoker command() {
        return new CLICommandInvoker(j, new ListItemsCommand());
    }

    @Test
    public void getAllJobs() throws Exception {

        j.createFreeStyleProject("top_level_project").setDisplayName("Top Level Project");

        ListView view = new ListView("view", j.jenkins);
        view.add(j.createFreeStyleProject("project_in_view"));
        j.jenkins.addView(view);

        MockFolder folder = j.createFolder("folder");
        folder.createProject(FreeStyleProject.class, "project_in_folder");

        Result result = command().invoke();

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("top_level_project", "project_in_view", "folder"));

        result = command().invokeWithArgs("-r");

        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("top_level_project", "project_in_view", "folder", "folder/project_in_folder"));
    }

    @Test
    public void failForNonexistingName() throws Exception {

        Result result = command().invokeWithArgs("--folder", "NoSuchItemGroup");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No folder named 'NoSuchItemGroup' found"));

        result = command().invokeWithArgs("--view", "NoSuchView");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view named NoSuchView inside view Jenkins"));

        result = command().invokeWithArgs("--view", "NoSuchView", "--folder", "NoSuchItemGroup");
        assertThat(result, failedWith(-1));

        j.createFolder("folderA");
        result = command().invokeWithArgs("--folder", "folderA", "--view", "NoSuchView");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view named NoSuchView inside view folderA"));

        j.jenkins.addView(new ListView("flat_view", j.jenkins));
        result = command().invokeWithArgs("--view", "flat_view/inner");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("flat_view view can not contain views"));

        j.jenkins.addView(new MockViewGroup("nested_view", j.jenkins));
        result = command().invokeWithArgs("--view", "nested_view/inner");
        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No view named inner inside view nested_view"));
    }

    @Test
    @Bug(18393)
    public void failForMatrixProject() throws Exception {

        MatrixProject project = j.createMatrixProject("MatrixProject");
        project.setAxes(new AxisList(new TextAxis("axis", "a", "b", "c")));

        Result result = command().invokeWithArgs("--folder", "MatrixProject");

        assertThat(result, failedWith(-1));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("No folder named 'MatrixProject' found"));
    }

    @Test
    public void getJobsFromFolderRecursively() throws Exception {

        j.createFreeStyleProject("top_level_project");

        MockFolder folder = j.createFolder("MyFolder");
        folder.createProject(FreeStyleProject.class, "project_in_folder");

        folder.createProject(MockFolder.class, "InnerFolder")
                .createProject(FreeStyleProject.class, "deeply_nested_project")
        ;

        Result result = command().invokeWithArgs("--folder", "MyFolder");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs(
                "MyFolder/project_in_folder", "MyFolder/InnerFolder"
        ));

        result = command().invokeWithArgs("--recursive", "--folder", "MyFolder");
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

        Result result = command().invokeWithArgs("--view", "MyViewGroup");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("project_in_view"));

        result = command().invokeWithArgs("--recursive", "--view", "MyViewGroup");
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
        assertThat(result.stderr(), containsString("This command is deprecated."));
    }

    @Test
    public void showDisplayNames() throws Exception {

        MockFolder folder = j.createFolder("outer_folder");
        folder.setDisplayName("Outer Folder");

        FreeStyleProject project = folder.createProject(FreeStyleProject.class, "project");
        project.setDisplayName("A Project");

        Result result = command().invokeWithArgs("--show-display-names");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Outer Folder"));
        assertThat(result.stdout(), not(containsString("outer_folder")));

        result = command().invokeWithArgs("-f", "outer_folder", "--show-display-names", "--recursive");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Outer Folder » A Project"));
        assertThat(result.stdout(), not(containsString("outer_folder")));
        assertThat(result.stdout(), not(containsString("project")));

        result = command().invokeWithArgs("--show-display-names", "--recursive");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Outer Folder"));
        assertThat(result.stdout(), containsString("Outer Folder » A Project"));
        assertThat(result.stdout(), not(containsString("outer_folder")));
        assertThat(result.stdout(), not(containsString("project")));
    }

    @Test
    public void listViewInsideFolder() throws Exception {

        MockFolder folder = j.createFolder("folder");

        ListView view = new ListView("view", folder);
        folder.addView(view);
        view.add(folder.createProject(FreeStyleProject.class, "in_view"));

        MockViewGroup nestedView = new MockViewGroup("nested_view", folder);
        folder.addView(nestedView);
        nestedView.getItems().add(folder.createProject(FreeStyleProject.class, "in_nested_view"));

        ListView leafView = new ListView("leaf_view", nestedView);
        nestedView.getViews().add(leafView);
        leafView.add(folder.createProject(FreeStyleProject.class, "deeply_nested"));

        Result result = command().invokeWithArgs("--folder", "folder", "--recursive");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("folder/in_view", "folder/in_nested_view", "folder/deeply_nested"));

        result = command().invokeWithArgs("--folder", "folder", "--view", "nested_view", "--recursive");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("folder/in_nested_view", "folder/deeply_nested"));

        result = command().invokeWithArgs("--folder", "folder", "--view", "nested_view/leaf_view");
        assertThat(result, succeeded());
        assertThat(result, hasNoErrorOutput());
        assertThat(result, listsOnlyJobs("folder/deeply_nested"));
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
