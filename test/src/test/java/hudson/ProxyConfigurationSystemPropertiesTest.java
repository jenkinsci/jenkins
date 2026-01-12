package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyConfigurationSystemPropertiesTest {

    private Properties originalProperties;

    @BeforeEach
    void backupProperties() {
        originalProperties = (Properties) System.getProperties().clone();
        clearProxyProperties();
    }

    @AfterEach
    void restoreProperties() {
        System.setProperties(originalProperties);
    }

    @Test
    void returnsNullWhenNoPropertiesSet() {
        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();
        assertThat(cfg, is(nullValue()));
    }

    @Test
    void usesHttpProperties() {
        System.setProperty("http.proxyHost", "http.example.com");
        System.setProperty("http.proxyPort", "8080");
        System.setProperty("http.proxyUser", "httpUser");
        System.setProperty("http.proxyPassword", "httpPass");
        System.setProperty("http.nonProxyHosts", "localhost|*.internal");

        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();

        assertThat(cfg, notNullValue());
        assertThat(cfg.getName(), is("http.example.com"));
        assertThat(cfg.getPort(), is(8080));
        assertThat(cfg.getUserName(), is("httpUser"));
        assertThat(cfg.getSecretPassword().getPlainText(), is("httpPass"));
        assertThat(cfg.getNoProxyHost(), is("localhost|*.internal"));
    }

    @Test
    void httpsOverridesHttpWhenPresent() {
        System.setProperty("http.proxyHost", "http.example.com");
        System.setProperty("http.proxyPort", "8080");
        System.setProperty("https.proxyHost", "https.example.com");
        System.setProperty("https.proxyPort", "8443");

        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();

        assertThat(cfg, notNullValue());
        assertThat(cfg.getName(), is("https.example.com"));
        assertThat(cfg.getPort(), is(8443));
    }

    @Test
    void invalidPortFallsBackToDefault() {
        System.setProperty("http.proxyHost", "http.example.com");
        System.setProperty("http.proxyPort", "NOT_A_NUMBER");

        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();

        assertThat(cfg, notNullValue());
        assertThat(cfg.getPort(), is(80));
    }

    @Test
    void trimsWhitespaceFromHostAndPort() {
        System.setProperty("http.proxyHost", "  trimmed.example.com  ");
        System.setProperty("http.proxyPort", "  9090  ");

        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();

        assertThat(cfg, notNullValue());
        assertThat(cfg.getName(), is("trimmed.example.com"));
        assertThat(cfg.getPort(), is(9090));
    }

    private void clearProxyProperties() {
        String[] keys = {
            "http.proxyHost", "http.proxyPort", "http.proxyUser", "http.proxyPassword", "http.nonProxyHosts",
            "https.proxyHost", "https.proxyPort", "https.proxyUser", "https.proxyPassword"
        };
        for (String k : keys) {
            System.clearProperty(k);
        }
    }

    @Test
    void whitespaceOnlyHostIsTreatedAsMissing() {
        System.setProperty("http.proxyHost", "   ");

        ProxyConfiguration cfg = ProxyConfiguration.createFromSystemProperties();

        assertThat(cfg, is(nullValue()));
    }

}
