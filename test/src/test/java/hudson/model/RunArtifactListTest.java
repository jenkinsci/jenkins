package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.Launcher;
import hudson.tasks.ArtifactArchiver;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The list of the artifacts of a build, which the page of a build loads from {@code artifactList.jelly}.
 */
@WithJenkins
class RunArtifactListTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void runningBuildExplainsThatTheListMayBeIncomplete() throws Exception {
        String html = listOfARunningBuild();

        assertThat(html, containsString("The build is still in progress"));
        assertThat(html, not(containsString("<p>building")));
    }

    @Test
    void linkToViewAnArtifactIsTranslated() throws Exception {
        String german = ResourceBundle.getBundle("hudson.model.Run.artifactList", Locale.GERMAN).getString("view");
        assertThat(german, not("view"));

        // JenkinsRule does not take the language from the request, the texts follow the default locale
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMAN);
        try {
            assertThat(listOfARunningBuild(), containsString(">" + german + "</a>"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    /**
     * Asks for the list while the build is still running, after it has archived a file.
     */
    private String listOfARunningBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        OneShotEvent archived = new OneShotEvent();
        OneShotEvent finish = new OneShotEvent();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                build.getWorkspace().child("f").write("content", "UTF-8");
                new ArtifactArchiver("f").perform(build, build.getWorkspace(), build.getEnvironment(listener), launcher, listener);
                archived.signal();
                finish.block();
                return true;
            }
        });
        Future<FreeStyleBuild> build = p.scheduleBuild2(0);
        archived.block();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            return wc.goTo(p.getUrl() + "1/artifactList?caption=Artifacts", "text/html").getWebResponse().getContentAsString();
        } finally {
            finish.signal();
            j.assertBuildStatusSuccess(build);
        }
    }
}
