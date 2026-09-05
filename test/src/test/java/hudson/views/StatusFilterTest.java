package hudson.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class StatusFilterTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void basic() throws Exception {
        List<TopLevelItem> list = new ArrayList<>();
        FreeStyleProject p1 = j.createFreeStyleProject("p1");
        FreeStyleProject p2 = j.createFreeStyleProject("p2");
        FreeStyleProject disabled = j.createFreeStyleProject("disabled");
        disabled.disable();

        list.add(p1);
        list.add(p2);
        list.add(disabled);

        StatusFilter enableFilter = new StatusFilter(true);
        StatusFilter disableFilter = new StatusFilter(false);

        List<TopLevelItem> filtered = enableFilter.filter(list, null, null);
        assertThat(filtered, containsInAnyOrder(p1, p2));

        filtered = disableFilter.filter(list, null, null);
        assertThat(filtered, containsInAnyOrder(disabled));
    }
}
