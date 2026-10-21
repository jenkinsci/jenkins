/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thoughtworks.xstream.security.ForbiddenClassException;
import hudson.util.XStream2;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.util.xstream.CriticalXStreamException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-4125")
class Security4125Test {

    public static class Holder {
        Holder owner;
    }

    public static class ConfigHolder {
        List<String> items = new ArrayList<>();
        Map<String, String> settings = new HashMap<>();
        String name;
        int count;
    }

    @Test
    void resolvesToBlacklistedTypeIsRejectedWithRealClassFilter(JenkinsRule j) {
        XStream2 xs = new XStream2();
        xs.alias("hold", Holder.class);

        CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () ->
                xs.fromXML("<hold>"
                        + "<owner resolves-to=\"com.google.common.collect.ImmutableSortedMultiset$SerializedForm\"/>"
                        + "</hold>"));
        assertThat(ex.getMessage(), containsString("cause-exception     : " + ForbiddenClassException.class.getName()));
    }

    @Test
    void jreInterfaceTypeDoesNotCauseCriticalExceptionFromTypePermission(JenkinsRule j) {
        XStream2 xs = new XStream2();
        xs.alias("hold", Holder.class);

        assertDoesNotThrow(() -> xs.fromXML("<hold><owner class=\"java.util.Comparator\"/></hold>"));
    }

    @Test
    void whitelistedJreTypesRoundTripWithRealClassFilter(JenkinsRule j) {
        XStream2 xs = new XStream2();
        xs.alias("config", ConfigHolder.class);

        ConfigHolder original = new ConfigHolder();
        original.items.add("alpha");
        original.items.add("beta");
        original.settings.put("key", "value");
        original.name = "test";
        original.count = 7;

        String xml = xs.toXML(original);
        ConfigHolder restored = assertDoesNotThrow(() -> (ConfigHolder) xs.fromXML(xml));

        assertThat(restored.name, is("test"));
        assertThat(restored.count, is(7));
        assertThat(restored.items.size(), is(2));
        assertThat(restored.items.get(0), is("alpha"));
        assertThat(restored.settings.get("key"), is("value"));
    }

    @Test
    void resolvesToBlacklistedTypeIsStillRejectedWhenTypePermissionSuppressed(JenkinsRule j) {
        boolean saved = XStream2.SUPPRESS_TYPE_PERMISSION;
        try {
            XStream2.SUPPRESS_TYPE_PERMISSION = true;
            XStream2 xs = new XStream2(); // picks up SUPPRESS_TYPE_PERMISSION at construction time
            xs.alias("hold", Holder.class);

            CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () ->
                    xs.fromXML("<hold>"
                            + "<owner resolves-to=\"com.google.common.collect.ImmutableSortedMultiset$SerializedForm\"/>"
                            + "</hold>"));
            assertThat(ex.getMessage(),
                    containsString("Refusing to instantiate resolves-to type "
                            + "'com.google.common.collect.ImmutableSortedMultiset$SerializedForm'"));
            assertThat(ex.getMessage(), not(containsString(ForbiddenClassException.class.getName())));
        } finally {
            XStream2.SUPPRESS_TYPE_PERMISSION = saved;
        }
    }

    @Test
    void exploitPayloadIsRejectedAtSelfMyViewsEndpoint(JenkinsRule j, @TempDir File tempDir) throws Exception {
        // TODO Keep in or remove?
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("attacker"));

        File marker = new File(tempDir, "rce-marker");
        assertFalse(marker.exists(), "pre-condition: marker must not exist");

        // Exploit XML from the original report, adapted to write to the test temp dir.
        String exploitXml = "<view>\n"
                + "  <owner resolves-to=\"com.google.common.collect.ImmutableSortedMultiset$SerializedForm\">\n"
                + "    <object resolves-to=\"org.apache.commons.collections.comparators.ComparableComparator\"/>\n"
                + "    <object resolves-to=\"org.apache.commons.collections.functors.InvokerTransformer\">\n"
                + "      <iMethodName>exec</iMethodName>\n"
                + "      <iParamTypes><java-class>java.lang.String</java-class></iParamTypes>\n"
                + "      <iArgs><string>/usr/bin/touch " + marker.getAbsolutePath() + "</string></iArgs>\n"
                + "    </object>\n"
                + "    <object resolves-to=\"org.apache.commons.collections.comparators.TransformingComparator\">\n"
                + "      <decorated reference=\"../../object[1]\"/>\n"
                + "      <transformer reference=\"../../object[2]\"/>\n"
                + "    </object>\n"
                + "    <object resolves-to=\"java.lang.Runtime\"/>\n"
                + "    <comparator reference=\"../object[3]\"/>\n"
                + "    <elements><object reference=\"../../object[4]\"/></elements>\n"
                + "    <counts><int>1</int></counts>\n"
                + "  </owner>\n"
                + "</view>";

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("attacker").withThrowExceptionOnFailingStatusCode(false)) {

            WebRequest req = new WebRequest(URI.create(wc.getContextPath() + "user/attacker/my-views/createView?name=exploittest").toURL(), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody(exploitXml);

            Page response = wc.getPage(req);

            // CriticalXStreamException must propagate to HTTP 500; the view must not be created.
            assertThat(response.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        }

        assertFalse(marker.exists(), "exploit must not have created the marker file");
    }
}
