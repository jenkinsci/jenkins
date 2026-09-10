package jenkins.security.csp;

import static jenkins.security.csp.AvatarContributor.extractDomainFromUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;

@For(AvatarContributor.class)
public class AvatarContributorTest {

    @Test
    void testExtractDomainFromUrl_Https() {
        assertThat(extractDomainFromUrl("https://example.com/path/to/avatar.png"), is("https://example.com"));
    }

    @Test
    void testExtractDomainFromUrl_Http() {
        assertThat(extractDomainFromUrl("http://example.com/path/to/avatar.png"), is("http://example.com"));
    }

    @Test
    void testExtractDomainFromUrl_WithPort() {
        assertThat(extractDomainFromUrl("https://example.com:8080/avatar.png"), is("https://example.com:8080"));
    }

    @Test
    void testExtractDomainFromUrl_WithQueryParameters() {
        assertThat(extractDomainFromUrl("https://example.com/avatar.png?size=64&format=png"), is("https://example.com"));
    }

    @Test
    void testExtractDomainFromUrl_WithFragment() {
        assertThat(extractDomainFromUrl("https://example.com/avatar.png#section"), is("https://example.com"));
    }

    @Test
    void testExtractDomainFromUrl_NullUrl() {
        assertThat(extractDomainFromUrl(null), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_NoHost() {
        assertThat(extractDomainFromUrl("/local/path/avatar.png"), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_UnsupportedScheme() {
        assertThat(extractDomainFromUrl("ftp://example.com/avatar.png"), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_FileScheme() {
        assertThat(extractDomainFromUrl("file:///path/to/avatar.png"), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_DataUri() {
        assertThat(extractDomainFromUrl("data:image/png;base64,iVBORw0KG..."), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_InvalidUri() {
        assertThat(extractDomainFromUrl("not a valid uri:::"), is(nullValue()));
    }

    @Test
    void testExtractDomainFromUrl_Subdomain() {
        assertThat(extractDomainFromUrl("https://cdn.example.com/avatar.png"), is("https://cdn.example.com"));
    }

    @Test
    void testExtractDomainFromUrl_Ipv4Address() {
        assertThat(extractDomainFromUrl("https://192.168.1.1/avatar.png"), is("https://192.168.1.1"));
    }

    @Test
    void testExtractDomainFromUrl_Ipv4WithPort() {
        assertThat(extractDomainFromUrl("https://192.168.1.1:8080/avatar.png"), is("https://192.168.1.1:8080"));
    }

    @Test
    void testExtractDomainFromUrl_CaseInsensitivity() {
        assertThat(extractDomainFromUrl("hTTps://EXAMPLE.com/path/to/avatar.png"), is("https://example.com"));
    }

    @Test
    void testNormalizeUrl_BasicHttps() {
        assertThat(
            AvatarContributor.normalizeUrl("https://example.com/avatars/u1.png"), is("https://example.com/avatars/u1.png")
        );
    }

    @Test
    void testNormalizeUrl_PreservesEncodedPathAndQuery() {
        assertThat(
            AvatarContributor.normalizeUrl("https://example.com/a%20b.png?size=64"), is("https://example.com/a%20b.png?size=64")
        );
    }

    @Test
    void testNormalizeUrl_StripsFragment() {
        assertThat(
            AvatarContributor.normalizeUrl("https://example.com/a.png#fragment"), is("https://example.com/a.png")
        );
    }

    @Test
    void testNormalizeUrl_RejectsUserInfo() {
        assertThat(
            AvatarContributor.normalizeUrl("https://user:pass@example.com/a.png"), is(nullValue())
        );
    }

    @Test
    void testNormalizeUrl_RejectsUnsupportedScheme() {
        assertThat(
            AvatarContributor.normalizeUrl("ftp://example.com/a.png"), is(nullValue())
        );
    }

    @Test
    void testNormalizeUrl_OmitsDefaultHttpsPort() {
        assertThat(
            AvatarContributor.normalizeUrl("https://example.com:443/a.png"), is("https://example.com/a.png")
        );
    }

    @Test
    void testNormalizeUrl_OmitsDefaultHttpPort() {
        assertThat(
            AvatarContributor.normalizeUrl("http://example.com:80/a.png"), is("http://example.com/a.png")
        );
    }

    @Test
    void testNormalizeUrl_KeepsNonDefaultPort() {
        assertThat(
            AvatarContributor.normalizeUrl("https://example.com:8443/a.png"), is("https://example.com:8443/a.png")
        );
    }

    // This test does not assert the exact full string. Its purpose is to verify that IPv6 literals are correctly bracketed,
    // while the path and other components are covered by separate tests.
    @Test
    void testNormalizeUrl_Ipv6HostIsBracketed() {
        String normalized = AvatarContributor.normalizeUrl("https://[2001:db8::1]/a.png");
        assertThat(normalized, startsWith("https://[2001:db8::1]"));
        assertThat(normalized, containsString("/a.png"));
    }

    // Verifies that the Unicode hostname is converted to its ASCII representation while preserving the
    // rest of the URL structure.
    @Test
    void testNormalizeUrl_IdnIsConvertedToAscii() {
        String normalized = AvatarContributor.normalizeUrl("https://例え.テスト/a.png");
        assertThat(normalized, startsWith("https://xn--r8jz45g.xn--zckzah"));
    }

    @Test
    void testAddNormalizedUrl_AddsValidUrl() {
        Set<String> set = new HashSet<>();

        boolean added = AvatarContributor.addNormalizedUrl("https://example.com/a.png", set);

        assertThat(added, is(true));
        assertThat(set, contains("https://example.com/a.png"));
    }

    @Test
    void testAddNormalizedUrl_RejectsInvalidUrl() {
        Set<String> set = new HashSet<>();

        boolean added = AvatarContributor.addNormalizedUrl("ftp://example.com/a.png", set);

        assertThat(added, is(false));
        assertThat(set, empty());
    }

    @Test
    void testAddNormalizedUrl_Deduplicates() {
        Set<String> set = new HashSet<>();

        AvatarContributor.addNormalizedUrl("https://example.com/a.png", set);
        boolean addedAgain = AvatarContributor.addNormalizedUrl("https://example.com/a.png", set);

        assertThat(addedAgain, is(false));
        assertThat(set.size(), is(1));
    }

}
