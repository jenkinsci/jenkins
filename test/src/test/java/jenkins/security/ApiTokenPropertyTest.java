package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.DownloadedContent;
import com.gargoylesoftware.htmlunit.HttpWebConnection;
import com.gargoylesoftware.htmlunit.WebConnection;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebResponseData;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.util.UrlUtils;
import hudson.Util;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTokenPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the UI interaction and authentication.
     */
    @Test
    public void basics() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.get("foo");
        ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // make sure the UI shows the token
        HtmlPage config = j.createWebClient().goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValueAttribute());

        // round-trip shouldn't change the API token
        j.submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));

        WebClient wc = j.createWebClient();
        configureWebConnection(wc, token);

        // test the authentication
        assertEquals(u, wc.executeOnServer(new Callable<User>() {
            public User call() throws Exception {
                return User.current();
            }
        }));
    }

    @Test
    public void security49Upgrade() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.get("foo");
        String historicalInitialValue = Util.getDigestOf(Jenkins.getInstance().getSecretKey() + ":" + u.getId());

        // we won't accept historically used initial value as it may be compromised
        ApiTokenProperty t = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t);
        String apiToken1 = t.getApiToken();
        assertFalse(apiToken1.equals(Util.getDigestOf(historicalInitialValue)));

        // the replacement for the compromised value must be consistent and cannot be random
        ApiTokenProperty t2 = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t2);
        assertEquals(apiToken1,t2.getApiToken());

        // any other value is OK. those are changed values
        t = new ApiTokenProperty(historicalInitialValue+"somethingElse");
        u.addProperty(t);
        assertTrue(t.getApiToken().equals(Util.getDigestOf(historicalInitialValue+"somethingElse")));
    }

    private void configureWebConnection(final WebClient wc, final String token) throws IOException {
        // See https://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html
        final UsernamePasswordCredentials fooCreds = new UsernamePasswordCredentials("foo", token);

        URL hostUrl = j.getURL();
        final HttpHost targetHost = new HttpHost(hostUrl.getHost(), hostUrl.getPort(), hostUrl.getProtocol());
        CredentialsProvider credsProvider = new BasicCredentialsProvider() {
            @Override
            public Credentials getCredentials(AuthScope authscope) {
                return fooCreds;
            }
        };
        credsProvider.setCredentials(
                new AuthScope("localhost", AuthScope.ANY_PORT, AuthScope.ANY_REALM),
                fooCreds);

        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        AuthScheme authScheme = new BasicScheme();
        authCache.put(targetHost, authScheme);

        // Add AuthCache to the execution context
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);

        wc.setCredentialsProvider(credsProvider);

        // Need to create our own WebConnection that gives us control of HttpClient execution,
        // allowing us to pass our own HttpClientContext etc. HttpWebConnection has its own
        // private HttpClientContext instance, which means we can't authenticate properly.
        wc.setWebConnection(new WebConnection() {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                try {
                    long startTime = System.currentTimeMillis();

                    HttpClientBuilder builder = HttpClientBuilder.create();
                    CloseableHttpClient httpClient = builder.build();
                    URL url = UrlUtils.encodeUrl(request.getUrl(), false, request.getCharset());
                    HttpGet method = new HttpGet(url.toURI());

                    CloseableHttpResponse response = httpClient.execute(targetHost, method, context);

                    HttpEntity httpEntity = response.getEntity();
                    DownloadedContent responseBody = HttpWebConnection.downloadContent(httpEntity.getContent(), wc.getOptions().getMaxInMemory());

                    String statusMessage = response.getStatusLine().getReasonPhrase();
                    if (statusMessage == null) {
                        statusMessage = "Unknown status message";
                    }
                    int statusCode = response.getStatusLine().getStatusCode();
                    List<NameValuePair> headers = new ArrayList<>();
                    for (final Header header : response.getAllHeaders()) {
                        headers.add(new NameValuePair(header.getName(), header.getValue()));
                    }

                    WebResponseData responseData = new WebResponseData(responseBody, statusCode, statusMessage, headers);
                    return new WebResponse(responseData, request, (System.currentTimeMillis() - startTime));
                } catch (Exception e) {
                    throw new AssertionError("Failed to execute WebRequest.", e);
                }
            }
        });
    }
}
