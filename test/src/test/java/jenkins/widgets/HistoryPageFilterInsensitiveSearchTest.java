package jenkins.widgets;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.search.UserSearchProperty;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AuthorizationStrategy;

/**
 * TODO: Code partially duplicated with HistoryPageFilterTest in core
 */
public class HistoryPageFilterInsensitiveSearchTest {

    private static final String TEST_USER_NAME = "testUser";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void should_search_insensitively_when_enabled_for_user() throws IOException {
        setUserContextAndAssertCaseInsensitivitySearchForGivenSearchString("failure");
    }

    @Test
    public void should_also_lower_search_query_in_insensitive_search_enabled() throws IOException {
        setUserContextAndAssertCaseInsensitivitySearchForGivenSearchString("FAILure");
    }

    private void setUserContextAndAssertCaseInsensitivitySearchForGivenSearchString(final String searchString) throws IOException {
        AuthorizationStrategy.Unsecured strategy = new AuthorizationStrategy.Unsecured();
        j.jenkins.setAuthorizationStrategy(strategy);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        UsernamePasswordAuthenticationToken testUserAuthentication = new UsernamePasswordAuthenticationToken(TEST_USER_NAME, "any");
        try (ACLContext acl = ACL.as(testUserAuthentication)) {
            User.get(TEST_USER_NAME).addProperty(new UserSearchProperty(true));

            //test logic
            List<ModelObject> runs = ImmutableList.<ModelObject>of(new MockRun(2, Result.FAILURE), new MockRun(1, Result.SUCCESS));
            assertOneMatchingBuildForGivenSearchStringAndRunItems(searchString, runs);
        }
    }

    private void assertOneMatchingBuildForGivenSearchStringAndRunItems(String searchString, List<ModelObject> runs) {
        //given
        HistoryPageFilter<ModelObject> historyPageFilter = new HistoryPageFilter<>(5);
        //and
        historyPageFilter.setSearchString(searchString);

        //when
        historyPageFilter.add(runs, Collections.<Queue.Item>emptyList());

        //then
        Assert.assertEquals(1, historyPageFilter.runs.size());
        Assert.assertEquals(HistoryPageEntry.getEntryId(2), historyPageFilter.runs.get(0).getEntryId());
    }

    @SuppressWarnings("unchecked")
    private static class MockRun extends Run {
        private final long queueId;

        public MockRun(long queueId) throws IOException {
            super(Mockito.mock(Job.class));
            this.queueId = queueId;
        }

        public MockRun(long queueId, Result result) throws IOException {
            this(queueId);
            this.result = result;
        }

        @Override
        public int compareTo(Run o) {
            return 0;
        }

        @Override
        public Result getResult() {
            return result;
        }

        @Override
        public boolean isBuilding() {
            return false;
        }

        @Override
        public long getQueueId() {
            return queueId;
        }

        @Override
        public int getNumber() {
            return (int) queueId;
        }
    }
}
