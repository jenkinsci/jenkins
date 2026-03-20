package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.is;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

public class WinstoneResponseHeaderLengthTest {

    @RegisterExtension
    public RealJenkinsExtension extension = new RealJenkinsExtension().addSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(jenkins.security.csp.winstoneResponseHeaderLengthTest.ContributorImpl.class));

    @Test
    void testLength() throws Exception {
        extension.startJenkins();
        String lastHeader = "";
        try (WebClient wc = new WebClient()) {
            // Hopefully speed this up a bit:
            wc.getOptions().setJavaScriptEnabled(false);
            wc.getOptions().setCssEnabled(false);
            wc.getOptions().setDownloadImages(false);
            wc.getPage(extension.getUrl()); // request once outside try/catch to ensure it works in principle
            try {
                while (true) {
                    final HtmlPage htmlPage = wc.getPage(extension.getUrl());
                    lastHeader = htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy");
                }
            } catch (FailingHttpStatusCodeException e) {
                assertThat(e.getStatusCode(), is(500));
                assertThat(e.getResponse().getContentAsString(), containsString("Error 500 Response Header Fields Too Large"));

                assertThat(lastHeader, hasLength(greaterThan(30_000)));
            }
        }
    }
}
