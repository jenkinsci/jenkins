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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ListView;
import hudson.model.Saveable;
import hudson.security.ACL;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.Jenkins;
import jenkins.util.xstream.CriticalXStreamException;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class RobustReflectionConverterTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-21024")
    @LocalData
    @Test
    void randomExceptionsReported() {
        FreeStyleProject p = r.jenkins.getItemByFullName("j", FreeStyleProject.class);
        assertNotNull(p);
        assertTrue(p.getTriggers().isEmpty(), "There should be no triggers");
        OldDataMonitor odm = (OldDataMonitor) r.jenkins.getAdministrativeMonitor("OldData");
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertEquals(Set.of(p), data.keySet());
        String text = data.values().iterator().next().extra;
        assertTrue(text.contains("hudson.triggers.TimerTrigger.readResolve"), text);
    }

    // Testing describable object to demonstrate what is expected with RobustReflectionConverter#addCriticalField
    // This should be configured with a specific keyword,
    // and should reject configurations with other keywords.
    // GUI related implementations (@DataBoundConstructor and newInstance) aren't used actually
    // (no jelly files are provides and they don't work actually),
    // but written to clarify a use case.
    public static class AcceptOnlySpecificKeyword implements Describable<AcceptOnlySpecificKeyword> {
        public static final String ACCEPT_KEYWORD = "accept";
        private final String keyword;

        @SuppressWarnings("checkstyle:redundantmodifier")
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

        @SuppressWarnings("checkstyle:redundantmodifier")
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
    void testRestInterfaceFailure() throws Exception {
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

            // Also not saved to disk as we serialize the object after load
            r.jenkins.reload();

            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNull(p.getProperty(KeywordProperty.class).getNonCriticalField());
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
            assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR,
                    page.getWebResponse().getStatusCode(),
                    "Submitting unacceptable configuration via REST should fail.");

            // Configuration should not be updated for a failure of the critical field,
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());

            r.jenkins.reload();

            // rejected configuration is not saved
            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotEquals("badvalue", p.getProperty(KeywordProperty.class).getCriticalField().getKeyword());
        }
    }

    @Test
    void testCliFailure() throws Exception {
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

            // Also not saved to disk as we serialize the object after load
            r.jenkins.reload();

            p = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNull(p.getProperty(KeywordProperty.class).getNonCriticalField());
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

    @Test
    void failObjectField() {
        final XStream2 xStream2 = new XStream2();
        final String xml = xStream2.toXML(new MyType("foo", "bar"));
        final CriticalXStreamException exception = assertThrows(CriticalXStreamException.class, () -> xStream2.fromXML(xml));
        assertThat(exception.getMessage(), containsString("Refusing to unmarshal type 'java.lang.String' to Object typed field 'foo' in 'hudson.util.RobustReflectionConverterTest$MyType'"));
    }

    @Test
    void successObjectFieldWithAllowlist() {
        final String className = MyType.class.getName();
        RobustReflectionConverter.SAFE_TYPES_WITH_OBJECT_FIELDS.add(className);
        try {
            final XStream2 xStream2 = new XStream2();
            final String xml = xStream2.toXML(new MyType("foo", "bar"));
            xStream2.fromXML(xml);
        } finally {
            RobustReflectionConverter.SAFE_TYPES_WITH_OBJECT_FIELDS.remove(className);
        }
    }

    @Test
    void successObjectFieldWithEscapeHatch() {
        RobustReflectionConverter.ALLOW_ALL_OBJECT_FIELDS = true;
        try {
            final XStream2 xStream2 = new XStream2();
            final String xml = xStream2.toXML(new MyType("foo", "bar"));
            xStream2.fromXML(xml);
        } finally {
            RobustReflectionConverter.ALLOW_ALL_OBJECT_FIELDS = false;
        }
    }

    static class MyType {
        private Object foo;
        private String bar;

        MyType(Object foo, String bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    @Test
    void successObjectFieldWithActualAnnotation() {
        final XStream2 xStream2 = new XStream2();
        final String xml = xStream2.toXML(new MyTypeWithActualAnnotation("foo", "bar"));
        xStream2.fromXML(xml);
    }

    static class MyTypeWithActualAnnotation {
        @jenkins.security.XstreamSafeObjectField
        private Object foo;
        private String bar;

        MyTypeWithActualAnnotation(Object foo, String bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    @Test
    void successObjectFieldWithSameNameAnnotation() {
        final XStream2 xStream2 = new XStream2();
        final String xml = xStream2.toXML(new MyTypeWithSameNameAnnotation("foo", "bar"));
        xStream2.fromXML(xml);
    }

    /** Marker annotation with same name as the real one, testing the case of plugin without updated core dependency */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface XstreamSafeObjectField {
    }

    static class MyTypeWithSameNameAnnotation {
        @XstreamSafeObjectField
        private Object foo;
        private String bar;

        MyTypeWithSameNameAnnotation(Object foo, String bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    public static class TypeA implements Describable<TypeA> {
        private final String value;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public TypeA(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<TypeA> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "TypeA";
            }
        }
    }

    public static class TypeB implements Describable<TypeB> {
        private final String value;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public TypeB(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<TypeB> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "TypeB";
            }
        }
    }

    @Test
    @Issue("SECURITY-3707")
    void testDescribableListGenericTypeConfusion() throws Exception {
        // TypeA is a Describable but not a ViewProperty
        String maliciousXml = "<?xml version='1.1' encoding='UTF-8'?>"
                + "<hudson.model.ListView>"
                + "<name>test</name>"
                + "<properties>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "<value>malicious</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "</properties>"
                + "</hudson.model.ListView>";

        ListView view = (ListView) Items.XSTREAM2.fromXML(maliciousXml);

        assertThat(view.getProperties(), empty());

        // Verify the rejected data is recorded in OldDataMonitor
        final OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        assertTrue(odm.isActivated());

        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertTrue(data.containsKey(view));
        String errorText = data.get(view).extra;
        assertThat(errorText, allOf(
                containsString("message             : Invalid type for CopyOnWriteList element"),
                containsString("required-type       : hudson.model.ViewProperty"),
                containsString("class               : hudson.util.RobustReflectionConverterTest$TypeA"),
                containsString("converter-type      : hudson.util.CopyOnWriteList$ConverterImpl")));
    }

    @Issue("SECURITY-3707")
    @Test
    void testCopyOnWriteListGenericTypeConfusion() throws Exception {
        // TypeA is not a JobProperty
        String maliciousXml = "<?xml version='1.1' encoding='UTF-8'?>"
                + "<project>"
                + "<properties>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "<value>malicious</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "</properties>"
                + "</project>";

        // Deserialize - TypeA should be rejected since it's not a JobProperty
        FreeStyleProject p = r.createFreeStyleProject();
        p.updateByXml(new StreamSource(
                new ByteArrayInputStream(maliciousXml.getBytes(StandardCharsets.UTF_8))));

        assertThat(p.getProperties().values(), empty());
    }

    @Issue("SECURITY-3707")
    @Test
    void testPersistedListGenericTypeConfusion() throws Exception {
        // JobPropertyWithPersistedList.items takes TypeA, not TypeB
        String maliciousXml = "<?xml version='1.1' encoding='UTF-8'?>"
                + "<project>"
                + "<properties>"
                + "<hudson.util.RobustReflectionConverterTest_-JobPropertyWithPersistedList>"
                + "<items>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<value>malicious</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "<value>expected</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "</items>"
                + "</hudson.util.RobustReflectionConverterTest_-JobPropertyWithPersistedList>"
                + "</properties>"
                + "</project>";

        FreeStyleProject p = r.createFreeStyleProject();
        p.updateByXml(new StreamSource(new ByteArrayInputStream(maliciousXml.getBytes(StandardCharsets.UTF_8))));

        JobPropertyWithPersistedList property = p.getProperty(JobPropertyWithPersistedList.class);
        assertThat(property, not(nullValue()));

        assertThat(property.items, not(hasItem(instanceOf(TypeB.class))));
        assertThat(property.items, hasItem(instanceOf(TypeA.class)));
    }

    @SuppressWarnings("checkstyle:redundantmodifier")
    public static class JobPropertyWithPersistedList extends JobProperty<FreeStyleProject> {
        public PersistedList<TypeA> items = new PersistedList<>();

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "Job Property With Persisted List";
            }
        }
    }

    @Test
    void testCopyOnWriteListGenericTypeConfusionOldDataMonitor() throws Exception {
        // TypeWithCopyOnWriteList takes TypeA elements, but deserialize TypeB
        String maliciousXml = "<?xml version='1.1' encoding='UTF-8'?>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeWithCopyOnWriteList>"
                + "<items>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<value>malicious</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "<value>expected</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "</items>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeWithCopyOnWriteList>";

        TypeWithCopyOnWriteList obj = (TypeWithCopyOnWriteList) Items.XSTREAM2.fromXML(maliciousXml);

        assertThat(obj.items, not(hasItem(instanceOf(TypeB.class))));
        assertThat(obj.items, hasItem(instanceOf(TypeA.class)));

        // Verify the rejected data is recorded in OldDataMonitor
        final OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        assertTrue(odm.isActivated());

        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertTrue(data.containsKey(obj));
        String errorText = data.get(obj).extra;
        assertThat(errorText, allOf(
                containsString("message             : Invalid type for CopyOnWriteList element"),
                containsString("required-type       : hudson.util.RobustReflectionConverterTest$TypeA"),
                containsString("class               : hudson.util.RobustReflectionConverterTest$TypeB"),
                containsString("converter-type      : hudson.util.CopyOnWriteList$ConverterImpl")));
    }

    @Test
    void testPersistedListGenericTypeConfusionOldDataMonitor() throws Exception {
        // TypeWithPersistedList takes TypeA elements, but deserialize TypeB
        String maliciousXml = "<?xml version='1.1' encoding='UTF-8'?>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeWithPersistedList>"
                + "<items>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<value>malicious</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeB>"
                + "<hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "<value>expected</value>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeA>"
                + "</items>"
                + "</hudson.util.RobustReflectionConverterTest_-TypeWithPersistedList>";

        TypeWithPersistedList obj = (TypeWithPersistedList) Items.XSTREAM2.fromXML(maliciousXml);

        assertThat(obj.items, not(hasItem(instanceOf(TypeB.class))));
        assertThat(obj.items, hasItem(instanceOf(TypeA.class)));

        // Verify the rejected data is recorded in OldDataMonitor
        final OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        assertTrue(odm.isActivated());

        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertTrue(data.containsKey(obj));
        String errorText = data.get(obj).extra;
        assertThat(errorText, allOf(
                containsString("message             : Invalid type for CopyOnWriteList element"),
                containsString("required-type       : hudson.util.RobustReflectionConverterTest$TypeA"),
                containsString("class               : hudson.util.RobustReflectionConverterTest$TypeB"),
                containsString("converter-type      : hudson.util.CopyOnWriteList$ConverterImpl")));
    }

    @SuppressWarnings("checkstyle:redundantmodifier")
    public static class TypeWithCopyOnWriteList implements Describable<TypeWithCopyOnWriteList>, Saveable {
        public CopyOnWriteList<TypeA> items = new CopyOnWriteList<>();

        @Override
        public void save() throws IOException {
            // No-op for testing
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<TypeWithCopyOnWriteList> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Type With CopyOnWriteList";
            }
        }
    }

    @SuppressWarnings("checkstyle:redundantmodifier")
    public static class TypeWithPersistedList implements Describable<TypeWithPersistedList>, Saveable {
        public PersistedList<TypeA> items = new PersistedList<>();

        @Override
        public void save() throws IOException {
            // No-op for testing
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<TypeWithPersistedList> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Type With PersistedList";
            }
        }
    }
}
