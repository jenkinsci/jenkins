package hudson.tasks.test;

import com.google.common.collect.ImmutableList;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Shell;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.test.helper.BuildPage;
import hudson.tasks.test.helper.ProjectPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class AggregatedTestResultPublisherTest {
    public static final String TEST_PROJECT_NAME = "junit";
    public static final String AGGREGATION_PROJECT_NAME = "aggregated";
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private FreeStyleProject upstreamProject;
    private FreeStyleProject downstreamProject;

    private FreeStyleBuild build;
    private JenkinsRule.WebClient wc;
    private static final String[] singleContents = {
            "abcdef"
    };
    private static final String[] singleFiles = {
            "test.txt"
    };
    private BuildPage buildPage;
    private ProjectPage projectPage;

    @Before
    public void setup() {
        wc = j.createWebClient();
    }

    @LocalData
    @Test
    public void aggregatedTestResultsOnly() throws Exception {
        createUpstreamProjectWithNoTests();
        createDownstreamProjectWithTests();

        buildAndSetupPageObjects();

        projectPage.getLatestAggregatedTestReportLink()
                .assertHasLatestTestResultText()
                .assertHasTests()
                .follow().hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);
        projectPage.assertNoTestReportLink();

        buildPage.getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertHasTests()
                .follow().hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);
        buildPage.assertNoTestReportLink();
    }

    @LocalData
    @Test
    public void testResultsOnly() throws Exception {
        createUpstreamProjectWithTests();
        createDownstreamProjectWithNoTests();

        buildAndSetupPageObjects();

        projectPage.getLatestTestReportLink()
                .assertHasLatestTestResultText()
                .assertHasTests()
                .follow();
        projectPage.assertNoAggregatedTestReportLink();

        buildPage.getTestReportLink()
                .assertHasTestResultText()
                .assertHasTests()
                .follow();
        buildPage.getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertNoTests();
    }

    @LocalData
    @Test
    public void testResultsAndAggregatedTestResults() throws Exception {
        createUpstreamProjectWithTests();
        createDownstreamProjectWithTests();

        buildAndSetupPageObjects();

        projectPage.getLatestTestReportLink()
                .assertHasLatestTestResultText()
                .assertHasTests()
                .follow();
        projectPage.assertNoAggregatedTestReportLink();

        buildPage.getTestReportLink()
                .assertHasTestResultText()
                .assertHasTests()
                .follow();
        buildPage.getAggregatedTestReportLink()
                .assertHasAggregatedTestResultText()
                .assertHasTests()
                .follow()
                .hasLinkToTestResultOfBuild(TEST_PROJECT_NAME, 1);
    }

    private void buildAndSetupPageObjects() throws Exception {
        buildOnce();
        projectPage = new ProjectPage(wc.getPage(upstreamProject));
        buildPage = new BuildPage(wc.getPage(build));
    }

    private void buildOnce() throws Exception {
        build = j.buildAndAssertSuccess(upstreamProject);
        j.waitUntilNoActivity();

        List<AbstractBuild<?, ?>> downstreamBuilds = ImmutableList.copyOf(build.getDownstreamBuilds(downstreamProject));
        assertThat(downstreamBuilds, hasSize(1));
    }


    private void createUpstreamProjectWithTests() throws Exception {
        createUpstreamProjectWithNoTests();
        addJUnitResultArchiver(upstreamProject);
    }

    private void createUpstreamProjectWithNoTests() throws Exception {
        upstreamProject = j.createFreeStyleProject(AGGREGATION_PROJECT_NAME);
        addFingerprinterToProject(upstreamProject, singleContents, singleFiles);
        upstreamProject.setQuietPeriod(0);
    }

    private void createDownstreamProjectWithTests() throws Exception {
        createDownstreamProjectWithNoTests();

        addJUnitResultArchiver(downstreamProject);
        j.jenkins.rebuildDependencyGraph();
    }

    private void createDownstreamProjectWithNoTests() throws Exception {
        downstreamProject = j.createFreeStyleProject(TEST_PROJECT_NAME);
        downstreamProject.setQuietPeriod(0);
        addFingerprinterToProject(downstreamProject, singleContents, singleFiles);

        upstreamProject.getPublishersList().add(new BuildTrigger(ImmutableList.of(downstreamProject), Result.SUCCESS));
        upstreamProject.getPublishersList().add(new AggregatedTestResultPublisher(TEST_PROJECT_NAME));

        j.jenkins.rebuildDependencyGraph();
    }

    private void addJUnitResultArchiver(FreeStyleProject project) {
        JUnitResultArchiver archiver = new JUnitResultArchiver("*.xml", false, null);
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    private void addFingerprinterToProject(FreeStyleProject project, String[] contents, String[] files) throws Exception {
        StringBuilder targets = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            project.getBuildersList().add(new Shell("echo " + contents[i] + " > " + files[i]));
            targets.append(files[i]).append(',');
        }

        project.getPublishersList().add(new Fingerprinter(targets.toString(), false));
    }
}
