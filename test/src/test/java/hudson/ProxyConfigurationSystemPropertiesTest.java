/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
            "https.proxyHost", "https.proxyPort", "https.proxyUser", "https.proxyPassword",
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
