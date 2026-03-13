package jenkins.security.csp;

import static jenkins.security.csp.AvatarContributor.extractDomainFromUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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
}
