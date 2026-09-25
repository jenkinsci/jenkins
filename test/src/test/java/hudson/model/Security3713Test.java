package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3713Test {

    private static final String TIME_ZONE = "Antarctica/Troll";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3713")
    void regularUserCannotAccessOtherUserViewNames() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice", "bob");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        MyViewsProperty aliceProperty = new MyViewsProperty(null);
        MyView customView = new MyView("Alice's View", aliceProperty);
        alice.addProperty(aliceProperty);
        aliceProperty.setUser(alice);
        aliceProperty.addView(customView);
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.login("bob");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + MyViewsProperty.class.getName() + "/fillPrimaryViewNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);

            assertThat(page.getWebResponse().getStatusCode(), is(403));
        }
    }

    @Test
    @Issue("SECURITY-3713")
    void adminCanAccessOtherUserViewNames() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice", "bob");
        authStrategy.grant(Jenkins.ADMINISTER).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        MyViewsProperty aliceProperty = new MyViewsProperty(null);
        MyView customView = new MyView("Alice's View", aliceProperty);
        alice.addProperty(aliceProperty);
        aliceProperty.setUser(alice);
        aliceProperty.addView(customView);
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("bob");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + MyViewsProperty.class.getName() + "/fillPrimaryViewNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);
            String body = page.getWebResponse().getContentAsString();

            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertThat(body, containsString("Alice's View"));
        }
    }

    @Test
    @Issue("SECURITY-3713")
    void sameUserCanAccessOwnViewNames() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        MyViewsProperty aliceProperty = new MyViewsProperty(null);
        MyView customView = new MyView("Alice's View", aliceProperty);
        alice.addProperty(aliceProperty);
        aliceProperty.setUser(alice);
        aliceProperty.addView(customView);
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + MyViewsProperty.class.getName() + "/fillPrimaryViewNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);
            String body = page.getWebResponse().getContentAsString();

            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertThat(body, containsString("Alice's View"));
        }
    }

    @Test
    @Issue("SECURITY-3713")
    void regularUserCannotAccessOtherUserTimeZone() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice", "bob");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        alice.addProperty(new TimeZoneProperty(TIME_ZONE));
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.login("bob");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + TimeZoneProperty.class.getName() + "/fillTimeZoneNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);

            assertThat(page.getWebResponse().getStatusCode(), is(403));
        }
    }

    @Test
    @Issue("SECURITY-3713")
    void adminCanAccessOtherUserTimeZone() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice", "bob");
        authStrategy.grant(Jenkins.ADMINISTER).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        alice.addProperty(new TimeZoneProperty(TIME_ZONE));
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("bob");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + TimeZoneProperty.class.getName() + "/fillTimeZoneNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);
            String body = page.getWebResponse().getContentAsString();

            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertThat(body, containsString("\"name\":\"" + TIME_ZONE + "\",\"selected\":true"));
        }
    }

    @Test
    @Issue("SECURITY-3713")
    void sameUserCanAccessOwnTimeZone() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User alice = User.getOrCreateByIdOrFullName("alice");

        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        alice.addProperty(new TimeZoneProperty(TIME_ZONE));
        alice.save();

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            URL url = new URL(j.getURL(), "user/alice/descriptorByName/" + TimeZoneProperty.class.getName() + "/fillTimeZoneNameItems");
            WebRequest request = new WebRequest(url, HttpMethod.POST);
            Page page = wc.getPage(request);
            String body = page.getWebResponse().getContentAsString();

            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertThat(body, containsString("\"name\":\"" + TIME_ZONE + "\",\"selected\":true"));
        }
    }
}
