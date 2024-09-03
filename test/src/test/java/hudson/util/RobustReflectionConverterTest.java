/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package hudson.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.cli.CLICommandInvoker;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Saveable;
import hudson.security.ACL;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class RobustReflectionConverterTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-21024")
    @LocalData
    @Test public void randomExceptionsReported() {
        FreeStyleProject p = r.jenkins.getItemByFullName("j", FreeStyleProject.class);
        assertNotNull(p);
        assertTrue("There should be no triggers", p.getTriggers().isEmpty());
        OldDataMonitor odm = (OldDataMonitor) r.jenkins.getAdministrativeMonitor("OldData");
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertEquals(Set.of(p), data.keySet());
        String text = data.values().iterator().next().extra;
        assertTrue(text, text.contains("hudson.triggers.TimerTrigger.readResolve"));
    }

    // Testing describable object to demonstrate what is expected with RobustReflectionConverter#addCriticalField
    // This should be configured with a specific keyword,
    // and should reject configurations with other keywords.
    // GUI related implementations (@DataBoundConstructor and newInstance) aren't used actually
    // (no jelly files are provides and they don't work actually),
    // but written to clarify a use case.
    public static class AcceptOnlySpecificKeyword extends AbstractDescribableImpl<AcceptOnlySpecificKeyword> {
        public static final String ACCEPT_KEYWORD = "accept";
        private final String keyword;

        @DataBoundConstructor
        public AcceptOnlySpecificKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getKeyword() {
            return keyword;
        }

        public boolean isAcceptable() {
            return ACCEPT_KEYWORD.equals(keyword);
        }

        private Object readResolve() throws Exception {
            if (!ACL.SYSTEM2.equals(Jenkins.getAuthentication2())) {
                // called via REST / CLI with authentication
                if (!isAcceptable()) {
                    // Reject invalid configuration via REST / CLI.
                    throw new Exception(String.format("Bad keyword: %s", getKeyword()));
                }
            }
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<AcceptOnlySpecificKeyword> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "AcceptOnlySpecificKeyword";
            }

            @Override
            public AcceptOnlySpecificKeyword newInstance(StaplerRequest2 req, JSONObject formData)
                    throws FormException {
                AcceptOnlySpecificKeyword instance = super.newInstance(req, formData);
                if (!instance.isAcceptable()) {
                    throw new FormException(String.format("Bad keyword: %s", instance.getKeyword()), "keyword");
                }
                return instance;
            }
        }
    }

    public static class KeywordProperty extends JobProperty<Job<?, ?>> {
        private final AcceptOnlySpecificKeyword nonCriticalField;
        private final AcceptOnlySpecificKeyword criticalField;

        public KeywordProperty(AcceptOnlySpecificKeyword nonCriticalField, AcceptOnlySpecificKeyword criticalField) {
            this.nonCriticalField = nonCriticalField;
            this.criticalField = criticalField;
        }

        public AcceptOnlySpecificKeyword getNonCriticalField() {
            return nonCriticalField;
        }

        public AcceptOnlySpecificKeyword getCriticalField() {
            return criticalField;
        }

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "KeywordProperty";
            }

            @Override
            public JobProperty<?> newInstance(StaplerRequest2 req, JSONObject formData)
                    throws FormException {
                // unfortunately, default newInstance bypasses newInstances for members.
                formData = formData.getJSONObject("keywordProperty");
                @SuppressWarnings("unchecked")
                Descriptor<AcceptOnlySpecificKeyword> d = Jenkins.get().getDescriptor(AcceptOnlySpecificKeyword.class);
                return new KeywordProperty(
                        d.newInstance(req, formData.getJSONObject("nonCriticalField")),
                        d.newInstance(req, formData.getJSONObject("criticalField"))
                );
            }
        }
    }

    private static final String CONFIGURATION_TEMPLATE =
            "<?xml version='1.1' encoding='UTF-8'?>"
            + "<project>"
            + "<properties>"
            +     "<hudson.util.RobustReflectionConverterTest_-KeywordProperty>"
            +         "<nonCriticalField>"
            +             "<keyword>%s</keyword>"
            +         "</nonCriticalField>"
            +         "<criticalField>"
            +             "<keyword>%s</keyword>"
            +         "</criticalField>"
            +     "</hudson.util.RobustReflectionConverterTest_-KeywordProperty>"
            + "</properties>"
            + "</project>";

    @Test
    public void testRestInterfaceFailure() throws Exception {
        Items.XSTREAM2.addCriticalField(KeywordProperty.class, "criticalField");

        // without addCriticalField. This is accepted.
        {
            FreeStyleProject p = r.createFreeStyleProject();
            p.addProperty(new KeywordProperty(
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD),
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD)
            ));
            p.save();

            // Configure a bad keyword via REST.
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            WebClient wc = r.createWebClient();
            wc.withBasicApiToken("test");
            WebRequest req = new WebRequest(new URI(wc.getContextPath() + String.format("%s/config.xml", p.getUrl())).toURL(), HttpMethod.POST);
            req.setEncodingType(null);
            req.setRequestBody(String.format(CONFIGURATION_TEMPLATE, "badvalue", AcceptOnlySpecificKeyword.ACCEPT_KEYWORD));
            wc.getPage(req);

            // AcceptOnlySpecificKeyword with bad value is not instantiated for rejected with readResolve,
            assertNull(p.getProperty(KeywordProperty.class).getNonCriticalField());
            assertEquals(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD, p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());

            // but save to the disk.
            r.jenkins.reload();

            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertEquals("badvalue", p.getProperty(KeywordProperty.class).getNonCriticalField().getKeyword());
            assertEquals(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD, p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());
        }

        // with addCriticalField. This is not accepted.
        {
            FreeStyleProject p = r.createFreeStyleProject();
            p.addProperty(new KeywordProperty(
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD),
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD)
            ));
            p.save();

            // Configure a bad keyword via REST.
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            WebClient wc = r.createWebClient()
                    .withThrowExceptionOnFailingStatusCode(false);
            wc.withBasicApiToken("test");
            WebRequest req = new WebRequest(new URI(wc.getContextPath() + String.format("%s/config.xml", p.getUrl())).toURL(), HttpMethod.POST);
            req.setEncodingType(null);
            req.setRequestBody(String.format(CONFIGURATION_TEMPLATE, AcceptOnlySpecificKeyword.ACCEPT_KEYWORD, "badvalue"));

            Page page = wc.getPage(req);
            assertEquals("Submitting unacceptable configuration via REST should fail.",
                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                    page.getWebResponse().getStatusCode());

            // Configuration should not be updated for a failure of the critical field,
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());

            r.jenkins.reload();

            // rejected configuration is not saved
            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());
        }
    }

    @Test
    public void testCliFailure() throws Exception {
        Items.XSTREAM2.addCriticalField(KeywordProperty.class, "criticalField");

        // without addCriticalField. This is accepted.
        {
            FreeStyleProject p = r.createFreeStyleProject();
            p.addProperty(new KeywordProperty(
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD),
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD)
            ));
            p.save();

            // Configure a bad keyword via CLI.
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());

            CLICommandInvoker.Result ret = new CLICommandInvoker(r, "update-job")
                    .asUser("test")
                    .withStdin(new ByteArrayInputStream(String.format(CONFIGURATION_TEMPLATE, "badvalue", AcceptOnlySpecificKeyword.ACCEPT_KEYWORD).getBytes(Charset.defaultCharset())))
                    .withArgs(
                            p.getFullName()
                    )
                    .invoke();

            assertEquals(0, ret.returnCode());

            // AcceptOnlySpecificKeyword with bad value is not instantiated for rejected with readResolve,
            assertNull(p.getProperty(KeywordProperty.class).getNonCriticalField());
            assertEquals(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD, p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());

            // but save to the disk.
            r.jenkins.reload();

            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertEquals("badvalue", p.getProperty(KeywordProperty.class).getNonCriticalField().getKeyword());
        }

        // with addCriticalField. This is not accepted.
        {
            FreeStyleProject p = r.createFreeStyleProject();
            p.addProperty(new KeywordProperty(
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD),
                    new AcceptOnlySpecificKeyword(AcceptOnlySpecificKeyword.ACCEPT_KEYWORD)
            ));
            p.save();

            // Configure a bad keyword via CLI.
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            CLICommandInvoker.Result ret = new CLICommandInvoker(r, "update-job")
                    .asUser("test")
                    .withStdin(new ByteArrayInputStream(String.format(CONFIGURATION_TEMPLATE, AcceptOnlySpecificKeyword.ACCEPT_KEYWORD, "badvalue").getBytes(Charset.defaultCharset())))
                    .withArgs(
                            p.getFullName()
                    )
                    .invoke();
            assertNotEquals(0, ret.returnCode());

            // Configuration should not be updated for a failure of the critical field,
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());

            r.jenkins.reload();

            // rejected configuration is not saved
            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());
        }
    }
}
