package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.ExtensionList;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@For(AvatarContributor.class)
@WithJenkins
public class AvatarContributorTest {

    @Test
    void testAllowWithDefaults_ValidUrl(JenkinsRule j) {
        LoggerRule loggerRule = new LoggerRule().record(AvatarContributor.class, Level.CONFIG).capture(100);
        AvatarContributor.allow("https://avatars.example.com/user/avatar.png");
        String csp = new CspBuilder().withDefaultContributions().build();
        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data: https://avatars.example.com; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
        assertThat(loggerRule, recorded(Level.CONFIG, is("Adding domain 'https://avatars.example.com' from avatar URL: https://avatars.example.com/user/avatar.png")));
    }

    @Test
    void testAllowWithDefaults_NullUrl(JenkinsRule j) {
        LoggerRule loggerRule = new LoggerRule().record(AvatarContributor.class, Level.FINEST).capture(100);
        AvatarContributor.allow(null);
        String csp = new CspBuilder().withDefaultContributions().build();
        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
        assertThat(loggerRule, recorded(Level.FINE, is("Skipping null domain in avatar URL: null")));
        assertThat(loggerRule, not(recorded(containsString("Adding domain "))));
        assertThat(loggerRule, not(recorded(containsString("Skipped adding duplicate domain "))));
    }

    @Test
    void testAllowWithDefaults_InvalidUrl(JenkinsRule j) {
        LoggerRule loggerRule = new LoggerRule().record(AvatarContributor.class, Level.FINE).capture(100);
        AvatarContributor.allow("not a valid url:::");

        CspBuilder cspBuilder = new CspBuilder().withDefaultContributions();
        String csp = cspBuilder.build();

        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
        assertThat(loggerRule, recorded(Level.FINE, is("Failed to parse avatar URI: not a valid url:::")));
    }

    @Test
    void testAllowWithDefaults_MultipleDomains(JenkinsRule j) {
        AvatarContributor.allow("https://avatars1.example.com/avatar1.png");
        AvatarContributor.allow("https://avatars2.example.com/avatar2.png");
        AvatarContributor.allow("https://avatars3.example.com/avatar3.png");

        String csp = new CspBuilder().withDefaultContributions().build();

        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data: https://avatars1.example.com https://avatars2.example.com https://avatars3.example.com; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
    }

    @Test
    void testAllowWithDefaults_DuplicateDomain(JenkinsRule j) {
        LoggerRule loggerRule = new LoggerRule().record(AvatarContributor.class, Level.FINEST).capture(100);
        AvatarContributor.allow("https://avatars.example.com/avatar1.png");
        AvatarContributor.allow("https://avatars.example.com/avatar2.png");

        CspBuilder cspBuilder = new CspBuilder().withDefaultContributions();
        String csp = cspBuilder.build();

        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data: https://avatars.example.com; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
        assertThat(loggerRule, recorded(Level.FINEST, is("Skipped adding duplicate domain 'https://avatars.example.com' from avatar URL: https://avatars.example.com/avatar2.png")));
    }

    @Test
    void testAllowWithDefaults_WithPort(JenkinsRule j) {
        AvatarContributor.allow("https://example.com:3000/avatar.png");

        CspBuilder cspBuilder = new CspBuilder().withDefaultContributions();
        String csp = cspBuilder.build();

        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data: https://example.com:3000; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
    }

    @Test
    void testAllowWithDefaults_HttpAndHttpsSameDomain(JenkinsRule j) {
        AvatarContributor.allow("http://example.com/avatar.png");
        AvatarContributor.allow("https://example.com/avatar.png");

        CspBuilder cspBuilder = new CspBuilder().withDefaultContributions();
        String csp = cspBuilder.build();

        assertThat(csp, is("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data: http://example.com https://example.com; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"));
    }

    @Test
    void testAllow_EmptyDomains(JenkinsRule j) {
        // Should produce empty CSP when no domains are added and no base contributors run
        assertThat(new CspBuilder().build(), is(""));
    }

    @Test
    void testAllow_SingleDomain(JenkinsRule j) {
        AvatarContributor.allow("https://cdn.example.org/avatars/user.png");

        CspBuilder cspBuilder = new CspBuilder().initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        ExtensionList.lookupSingleton(AvatarContributor.class).apply(cspBuilder);

        String csp = cspBuilder.build();
        assertThat(csp, is("default-src 'self'; img-src 'self' https://cdn.example.org;"));
    }

    @Test
    void testAllow_MultipleDomainsInImgSrc(JenkinsRule j) {
        AvatarContributor.allow("https://avatars-a.example.com/user1.png");
        AvatarContributor.allow("https://avatars-b.example.net/user2.png");
        AvatarContributor.allow("http://insecure.example.org:8080/user3.png");
        AvatarContributor.allow("https://avatars-b.example.net/user4.png");
        AvatarContributor.allow("http://insecure.example.org:8080/user5.png");

        CspBuilder cspBuilder = new CspBuilder().initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        ExtensionList.lookupSingleton(AvatarContributor.class).apply(cspBuilder);

        assertThat(cspBuilder.build(), is("default-src 'self'; img-src 'self' http://insecure.example.org:8080 https://avatars-a.example.com https://avatars-b.example.net;"));
    }

    @Test
    void testAllow_UnsupportedSchemeDoesNotAddToCsp(JenkinsRule j) {
        LoggerRule loggerRule = new LoggerRule().record(AvatarContributor.class, Level.FINER).capture(100);
        AvatarContributor.allow("ftp://files.example.com/avatar.png");
        AvatarContributor.allow("data:image/png;base64,iVBORw0KG...");

        CspBuilder cspBuilder = new CspBuilder().initialize(FetchDirective.DEFAULT_SRC, Directive.SELF);
        ExtensionList.lookupSingleton(AvatarContributor.class).apply(cspBuilder);
        String csp = cspBuilder.build();
        assertThat(csp, is("default-src 'self';"));
        assertThat(loggerRule, recorded(Level.FINER, is("Ignoring URI with unsupported scheme: ftp://files.example.com/avatar.png")));
        assertThat(loggerRule, recorded(Level.FINER, is("Ignoring URI without host: data:image/png;base64,iVBORw0KG...")));
    }
}
