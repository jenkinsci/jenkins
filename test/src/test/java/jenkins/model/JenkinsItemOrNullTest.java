package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;

@WithJenkins
class JenkinsItemOrNullTest {

    private static FreeStyleProject setUpUsers(JenkinsRule r) throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.DISCOVER).everywhere().to("visitor")
                .grant(Item.READ).everywhere().to("reader"));
        return r.createFreeStyleProject("p");
    }

    private static <T> T asUser(String user, Callable<T> action) throws Exception {
        try (var ignored = ACL.as2(User.getById(user, true).impersonate2())) {
            return action.call();
        }
    }

    @Test
    void readableItemIsReturned(JenkinsRule r) throws Exception {
        var job = setUpUsers(r);
        assertThat(asUser("reader", () -> r.jenkins.getItemOrNull("p")), sameInstance(job));
        assertThat(asUser("reader", () -> r.jenkins.getItemByFullNameOrNull("p")), sameInstance(job));
        assertThat(
                asUser("reader", () -> r.jenkins.getItemByFullNameOrNull("p", FreeStyleProject.class)),
                sameInstance(job));
        assertThat(
                asUser("reader", () -> r.jenkins.getItemOrNull("p", null, FreeStyleProject.class)),
                sameInstance(job));

        // item of another type is null
        assertThat(
                asUser("reader", () -> r.jenkins.getItemByFullNameOrNull("p", Folder.class)),
                nullValue());
        assertThat(
                asUser("reader", () -> r.jenkins.getItemOrNull("p", null, Folder.class)),
                nullValue());
    }

    @Test
    void discoverOnlyItemIsAbsentWhileGetItemThrows(JenkinsRule r) throws Exception {
        setUpUsers(r);
        assertThrows(AccessDeniedException.class, () -> asUser("visitor", () -> r.jenkins.getItem("p")));
        assertThrows(AccessDeniedException.class, () -> asUser("visitor", () -> r.jenkins.getItemByFullName("p")));
        assertThrows(
                AccessDeniedException.class,
                () -> asUser("visitor", () -> r.jenkins.getItemByFullName("p", FreeStyleProject.class)));
        assertThrows(
                AccessDeniedException.class,
                () -> asUser("visitor", () -> r.jenkins.getItem("p", (Item) null, FreeStyleProject.class)));
        assertThat(asUser("visitor", () -> r.jenkins.getItemOrNull("p")), nullValue());
        assertThat(asUser("visitor", () -> r.jenkins.getItemByFullNameOrNull("p")), nullValue());
        assertThat(
                asUser("visitor", () -> r.jenkins.getItemByFullNameOrNull("p", FreeStyleProject.class)),
                nullValue());
        assertThat(
                asUser("visitor", () -> r.jenkins.getItemOrNull("p", null, FreeStyleProject.class)),
                nullValue());
    }

    @Test
    void nestedDiscoverOnlyItemIsAbsent(JenkinsRule r) throws Exception {
        setUpUsers(r);
        var folder = r.createProject(Folder.class, "d");
        var nested = folder.createProject(FreeStyleProject.class, "q");
        assertThat(asUser("reader", () -> folder.getItemOrNull("q")), sameInstance(nested));
        assertThrows(AccessDeniedException.class, () -> asUser("visitor", () -> folder.getItem("q")));
        assertThat(asUser("visitor", () -> folder.getItemOrNull("q")), nullValue());
        assertThrows(AccessDeniedException.class, () -> asUser("visitor", () -> r.jenkins.getItemByFullName("d/q")));
        assertThrows(
                AccessDeniedException.class,
                () -> asUser("visitor", () -> r.jenkins.getItemByFullName("d/q", FreeStyleProject.class)));
        assertThrows(
                AccessDeniedException.class,
                () -> asUser("visitor", () -> r.jenkins.getItem("d/q", (Item) null, FreeStyleProject.class)));
        assertThat(asUser("visitor", () -> r.jenkins.getItemByFullNameOrNull("d/q")), nullValue());
        assertThat(asUser("visitor", () -> r.jenkins.getItemByFullNameOrNull("d/q", FreeStyleProject.class)), nullValue());
        assertThat(asUser("visitor", () -> r.jenkins.getItemOrNull("d/q", null, FreeStyleProject.class)), nullValue());
    }
}
