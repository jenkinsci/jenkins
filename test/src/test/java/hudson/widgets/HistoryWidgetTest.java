package hudson.widgets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import java.util.List;
import jenkins.model.HistoricalBuild;
import jenkins.widgets.DescriptionHistoryPageEntryDecorator;
import jenkins.widgets.HistoryPageEntry;
import jenkins.widgets.HistoryPageEntryDecorator;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class HistoryWidgetTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-15499")
    void moreLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        for (int x = 0; x < 3; x++) {
            j.buildAndAssertSuccess(p);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setJavaScriptEnabled(false);
        wc.goTo("job/" + p.getName() + "/buildHistory/all");
    }

    @Test
    void displayFilterInput() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient();

        { // Filter input shouldn't display when there's no build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector("#jenkins-builds .jenkins-search");
            assertTrue(searchInputContainer.getAttributes().getNamedItem("class").getNodeValue().contains("jenkins-hidden"));
        }

        j.buildAndAssertSuccess(p);  // Add a build

        { // Filter input should display when there's a build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector("#jenkins-builds .jenkins-search");
            assertFalse(searchInputContainer.getAttributes().getNamedItem("class").getNodeValue().contains("jenkins-hidden"));
        }
    }

    /** Each build gets one entry per decorator, in extension order, with {@code null} for an inapplicable decorator. */
    @Test
    void entryDecorators() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild first = j.buildAndAssertSuccess(p);
        FreeStyleBuild second = j.buildAndAssertSuccess(p);

        ExtensionList<HistoryPageEntryDecorator> registered = HistoryPageEntryDecorator.all();
        AlwaysDecorator always = registered.get(AlwaysDecorator.class);
        FirstBuildOnlyDecorator firstOnly = registered.get(FirstBuildOnlyDecorator.class);
        assertThat(always, notNullValue());
        assertThat(firstOnly, notNullValue());
        int descriptionIndex = registered.indexOf(registered.get(DescriptionHistoryPageEntryDecorator.class));
        assertThat(registered.indexOf(always), lessThan(descriptionIndex));
        assertThat(registered.indexOf(firstOnly), lessThan(descriptionIndex));

        j.executeOnServer(() -> {
            HistoryWidget<FreeStyleProject, FreeStyleBuild> widget = new HistoryWidget<>(p, p.getBuilds(), Job.HISTORY_ADAPTER);
            HistoryPageEntry<HistoricalBuild> firstEntry = new HistoryPageEntry<>(first);
            HistoryPageEntry<HistoricalBuild> secondEntry = new HistoryPageEntry<>(second);

            List<HistoryPageEntryDecorator.EntryContext> firstContexts = widget.getEntryDecorators(firstEntry);
            List<HistoryPageEntryDecorator.EntryContext> secondContexts = widget.getEntryDecorators(secondEntry);
            assertThat(firstContexts, hasSize(registered.size()));
            assertThat(secondContexts, hasSize(registered.size()));

            for (int i = 0; i < registered.size(); i++) {
                HistoryPageEntryDecorator.EntryContext context = firstContexts.get(i);
                if (context != null) {
                    assertThat(context.getDecorator(), sameInstance(registered.get(i)));
                    assertThat(context.getWidget(), sameInstance(widget));
                    assertThat(context.getPageEntry(), sameInstance(firstEntry));
                    assertThat(context.getBuild(), sameInstance(first));
                }
            }

            int alwaysIndex = registered.indexOf(always);
            int firstOnlyIndex = registered.indexOf(firstOnly);
            assertThat(firstContexts.get(alwaysIndex), notNullValue());
            assertThat(firstContexts.get(firstOnlyIndex), notNullValue());
            assertThat(firstContexts.get(firstOnlyIndex).getDecorator(), sameInstance(firstOnly));
            assertThat(secondContexts.get(alwaysIndex).getDecorator(), sameInstance(always));
            assertThat(secondContexts.get(alwaysIndex).getBuild(), sameInstance(second));
            assertThat(secondContexts.get(firstOnlyIndex), nullValue());
            return null;
        });
    }

    @TestExtension("entryDecorators")
    public static class AlwaysDecorator extends HistoryPageEntryDecorator {
    }

    @TestExtension("entryDecorators")
    public static class FirstBuildOnlyDecorator extends HistoryPageEntryDecorator {
        @Override
        public boolean isApplicable(@NonNull HistoryWidget<?, ?> widget, @NonNull HistoricalBuild build) {
            return build.getNumber() == 1;
        }
    }
}
