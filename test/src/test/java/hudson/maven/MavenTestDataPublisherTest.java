package hudson.maven;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.maven.reporters.SurefireAggregatedReport;
import hudson.model.*;
import hudson.tasks.Maven;
import hudson.tasks.junit.*;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.DataBoundConstructor;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Iterables.find;

public class MavenTestDataPublisherTest extends HudsonTestCase {

    private static final String ACTION_LABEL = "Label to find";

    @Bug(9767)
    public void testShouldContainTestAction() throws Exception {
        executeBuild("maven-surefire-unstable.zip");
        HtmlPage testReportPage = new WebClient().goTo("job/test0/org.jvnet.hudson.main.test.multimod.incr$moduleA/1/testReport/test/AppATest/testAppA/");
        assertNotNull("Test Action was not contributed", testReportPage.getAnchorByText(ACTION_LABEL));
    }

    private MavenModuleSetBuild executeBuild(String workspace) throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        configure(m);
        m.setScm(new ExtractResourceSCM(getClass().getResource(workspace)));
        m.setGoals("test");
        MavenModuleSetBuild b = m.scheduleBuild2(0).get();
        assertBuildStatus(Result.UNSTABLE, b);
        return b;
    }

    private void configure(MavenModuleSet m) throws Exception {
        HtmlForm form = new WebClient().getPage(m, "configure").getFormByName("config");
        form.getButtonByCaption("Add post-build action").click();
        HtmlElement additionalReports = find(form.getHtmlElementsByTagName("a"), withText("Additional test report features"));
        additionalReports.click();
        HtmlInput labelEnabler = form.getInputByName("hudson-maven-MavenTestDataPublisherTest$LabelTestDataPublisher");
        labelEnabler.setChecked(true);
        submit(form);
    }

    private Predicate<HtmlElement> withText(final String text) {
        return new Predicate<HtmlElement>() {
            public boolean apply(HtmlElement element) {
                return text.equals(element.getTextContent());
            }
        };
    }

    public static class LabelTestDataPublisher extends TestDataPublisher {

        @DataBoundConstructor
        public LabelTestDataPublisher() {
        }

        @Override
        public TestResultAction.Data getTestData(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, TestResult testResult) throws IOException, InterruptedException {
            return new TestResultAction.Data() {
                @Override
                public List<? extends TestAction> getTestAction(TestObject testObject) {
                    return Collections.singletonList(new TestAction() {
                        public String getIconFileName() {
                            return "icon.gif";
                        }

                        public String getDisplayName() {
                            return ACTION_LABEL;
                        }

                        public String getUrlName() {
                            return null;
                        }
                    });
                }
            };
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<TestDataPublisher> {

            @Override
            public String getDisplayName() {
                return ACTION_LABEL;
            }

        }
    }
}
