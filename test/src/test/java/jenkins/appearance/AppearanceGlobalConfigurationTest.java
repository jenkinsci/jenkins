/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.appearance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.security.Permission;
import java.net.URI;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class AppearanceGlobalConfigurationTest {

    private JenkinsRule j;

    @BeforeAll
    static void enablePermissions() {
        System.setProperty("jenkins.security.ManagePermission", "true");
        System.setProperty("jenkins.security.SystemReadPermission", "true");
    }

    @AfterAll
    static void disablePermissions() {
        System.clearProperty("jenkins.security.ManagePermission");
        System.clearProperty("jenkins.security.SystemReadPermission");
    }

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    // -------------------------------------------------------------------------
    // Test extensions
    // -------------------------------------------------------------------------

    /**
     * AppearanceCategory descriptor that explicitly requires Overall/Manage.
     * Has a single {@code value} string that is readable after the fact.
     */
    @TestExtension
    public static class ManageRequiringConfig extends GlobalConfiguration {

        private String value = "manage-default";

        public String getValue() {
            return value;
        }

        @DataBoundSetter
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            req.bindJSON(this, json);
            return true;
        }

        @NonNull
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(AppearanceCategory.class);
        }

        @NonNull
        @Override
        public Permission getRequiredGlobalConfigPagePermission() {
            return Jenkins.MANAGE;
        }

        static ManageRequiringConfig get() {
            return ExtensionList.lookupSingleton(ManageRequiringConfig.class);
        }
    }

    /**
     * AppearanceCategory descriptor that keeps the default Overall/Administer requirement.
     * Has a single {@code value} string that is readable after the fact.
     */
    @TestExtension
    public static class AdminRequiringConfig extends GlobalConfiguration {

        private String value = "admin-default";

        public String getValue() {
            return value;
        }

        @DataBoundSetter
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            req.bindJSON(this, json);
            return true;
        }

        @NonNull
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(AppearanceCategory.class);
        }

        // getRequiredGlobalConfigPagePermission() not overridden → defaults to ADMINISTER

        static AdminRequiringConfig get() {
            return ExtensionList.lookupSingleton(AdminRequiringConfig.class);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setupSecurity(MockAuthorizationStrategy strategy) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(strategy);
    }

    private JenkinsRule.WebClient webClient(String user) {
        return j.createWebClient().withBasicCredentials(user);
    }

    /**
     * Submits the Appearance form with a given JSON body, by-passing HtmlUnit form handling.
     * Caller is responsible for including a valid crumb (or disabling CSRF first).
     */
    private int postAppearanceConfigure(JenkinsRule.WebClient wc, String jsonBody) throws Exception {
        WebRequest req = new WebRequest(
                new URI(j.getURL() + "appearance/configure").toURL(),
                HttpMethod.POST);
        req.setRequestParameters(List.of(new NameValuePair("json", jsonBody)));
        try {
            wc.getPage(req);
            return 200;
        } catch (FailingHttpStatusCodeException e) {
            return e.getStatusCode();
        }
    }

    // -------------------------------------------------------------------------
    // Page visibility
    // -------------------------------------------------------------------------

    @Test
    void adminCanAccessPage() throws Exception {
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        webClient("admin").goTo("manage/appearance");
    }

    @Test
    void manageUserCanAccessPage() throws Exception {
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to("manager"));
        webClient("manager").goTo("manage/appearance");
    }

    @Test
    void systemReadUserCanAccessPage() throws Exception {
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.SYSTEM_READ, Jenkins.READ).everywhere().to("reader"));
        webClient("reader").goTo("manage/appearance");
    }

    @Test
    void readOnlyUserCannotAccessPage() throws Exception {
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("nobody"));
        JenkinsRule.WebClient wc = webClient("nobody");
        FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("manage/appearance"));
        assertEquals(403, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Rendering: which descriptors appear and whether inputs are editable
    // -------------------------------------------------------------------------

    @Test
    void adminSeesAllDescriptorsEditable() throws Exception {
        ManageRequiringConfig.get().setValue("manage-value");
        AdminRequiringConfig.get().setValue("admin-value");
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        HtmlPage page = webClient("admin").goTo("manage/appearance");
        String body = page.getWebResponse().getContentAsString();
        // Both values appear as editable inputs, not read-only blocks
        assertThat(body, containsString("value=\"manage-value\""));
        assertThat(body, containsString("value=\"admin-value\""));
        assertThat(body, not(containsString("jenkins-readonly")));
    }

    @Test
    void manageUserSeesOnlyManageDescriptorEditable() throws Exception {
        ManageRequiringConfig.get().setValue("manage-value");
        AdminRequiringConfig.get().setValue("admin-value");
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to("manager"));
        HtmlPage page = webClient("manager").goTo("manage/appearance");
        String body = page.getWebResponse().getContentAsString();
        // MANAGE-level value is present as an editable input
        assertThat(body, containsString("value=\"manage-value\""));
        assertThat(body, not(containsString("jenkins-readonly")));
        // ADMINISTER-level value is completely absent (descriptor not rendered)
        assertThat(body, not(containsString("admin-value")));
    }

    @Test
    void systemReadUserSeesBothDescriptorsReadOnly() throws Exception {
        ManageRequiringConfig.get().setValue("manage-value");
        AdminRequiringConfig.get().setValue("admin-value");
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.SYSTEM_READ, Jenkins.READ).everywhere().to("reader"));
        HtmlPage page = webClient("reader").goTo("manage/appearance");
        String body = page.getWebResponse().getContentAsString();
        // Both values are visible but rendered as read-only blocks, not inputs
        assertThat(body, containsString("jenkins-readonly"));
        assertThat(body, containsString("manage-value"));
        assertThat(body, containsString("admin-value"));
        assertThat(body, not(containsString("value=\"manage-value\"")));
        assertThat(body, not(containsString("value=\"admin-value\"")));
    }

    @Test
    void manageAndSystemReadSeesManageEditableAdminReadOnly() throws Exception {
        ManageRequiringConfig.get().setValue("manage-value");
        AdminRequiringConfig.get().setValue("admin-value");
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.SYSTEM_READ, Jenkins.READ).everywhere().to("mgr-reader"));
        HtmlPage page = webClient("mgr-reader").goTo("manage/appearance");
        String body = page.getWebResponse().getContentAsString();
        // MANAGE-level descriptor is editable
        assertThat(body, containsString("value=\"manage-value\""));
        // ADMINISTER-level descriptor is visible but read-only
        assertThat(body, containsString("jenkins-readonly"));
        assertThat(body, containsString("admin-value"));
        assertThat(body, not(containsString("value=\"admin-value\"")));
    }

    // -------------------------------------------------------------------------
    // Submission: permission enforcement on the submit path
    // -------------------------------------------------------------------------

    @Test
    void adminCanConfigureBothDescriptors() throws Exception {
        setupSecurity(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        ManageRequiringConfig.get().setValue("before-manage");
        AdminRequiringConfig.get().setValue("before-admin");

        JenkinsRule.WebClient wc = webClient("admin");
        HtmlPage page = wc.goTo("manage/appearance");
        HtmlForm form = page.getFormByName("config");
        // Each descriptor's inputs are preceded by a row-set-start div with its name; find the input
        // inside the sibling nodes that follow the marker div for each descriptor.
        String manageName = ManageRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        String adminName = AdminRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + manageName + "'] ~ * input[name='_.value']").setValue("new-manage");
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + adminName + "'] ~ * input[name='_.value']").setValue("new-admin");
        j.submit(form);

        assertEquals("new-manage", ManageRequiringConfig.get().getValue());
        assertEquals("new-admin", AdminRequiringConfig.get().getValue());
    }

    @Test
    void adminDescriptorPreservedWhenManageUserSubmits() throws Exception {
        // A MANAGE-only user's form submission must not overwrite or erase values that
        // belong to an ADMINISTER-level descriptor — the server must skip descriptors
        // the submitting user cannot edit, even if those keys are absent from the POST body.
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to("manager"));

        AdminRequiringConfig.get().setValue("admin-set-by-admin");
        ManageRequiringConfig.get().setValue("manage-initial");

        // Manager loads the form (admin descriptor is not rendered/included) and submits
        JenkinsRule.WebClient managerWc = webClient("manager");
        HtmlPage page = managerWc.goTo("manage/appearance");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        // The manage-level value round-trips fine
        assertEquals("manage-initial", ManageRequiringConfig.get().getValue());
        // The admin-level value must be completely untouched
        assertEquals("admin-set-by-admin", AdminRequiringConfig.get().getValue());
    }

    @Test
    void manageUserCanChangeManageLevelValue() throws Exception {
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to("manager"));

        ManageRequiringConfig.get().setValue("old-value");

        JenkinsRule.WebClient managerWc = webClient("manager");
        HtmlPage page = managerWc.goTo("manage/appearance");
        HtmlForm form = page.getFormByName("config");

        // The MANAGE-level input is named "_.value" and is the only input on this page for a manager
        form.getInputByName("_.value").setValue("new-value");
        j.submit(form);

        assertEquals("new-value", ManageRequiringConfig.get().getValue());
    }

    @Test
    void systemReadUserCannotSubmit() throws Exception {
        setupSecurity(new MockAuthorizationStrategy().grant(Jenkins.SYSTEM_READ, Jenkins.READ).everywhere().to("reader"));

        j.jenkins.setCrumbIssuer(null); // simplify CSRF for raw POST
        int status = postAppearanceConfigure(webClient("reader"), "{}");
        assertEquals(403, status);
    }

    @Issue("SECURITY-3981")
    @Test
    void droppingAdministerBeforeSubmitIgnoresAdminDescriptor() throws Exception {
        // Obtain a fully populated form as an ADMINISTER user (so the POST body genuinely contains
        // both descriptors' JSON, produced by Jenkins' own form serialisation), then drop ADMINISTER
        // down to MANAGE before submitting. The MANAGE-level change must take effect and the
        // ADMINISTER-level change must be ignored.
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("user"));

        ManageRequiringConfig.get().setValue("manage-initial");
        AdminRequiringConfig.get().setValue("admin-initial");

        JenkinsRule.WebClient wc = webClient("user");
        HtmlPage page = wc.goTo("manage/appearance");
        HtmlForm form = page.getFormByName("config");

        String manageName = ManageRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        String adminName = AdminRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + manageName + "'] ~ * input[name='_.value']").setValue("manage-edited");
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + adminName + "'] ~ * input[name='_.value']").setValue("admin-edited");

        // Same user, now only MANAGE — the already-rendered form still carries both descriptors
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.MANAGE, Jenkins.READ).everywhere().to("user"));

        j.submit(form);

        // The MANAGE-level edit is applied
        assertEquals("manage-edited", ManageRequiringConfig.get().getValue());
        // The ADMINISTER-level edit is dropped: the user no longer holds ADMINISTER
        assertEquals("admin-initial", AdminRequiringConfig.get().getValue());
    }

    @Issue("SECURITY-3981")
    @Test
    void keepingAdministerBeforeSubmitAppliesAdminDescriptor() throws Exception {
        // Control for droppingAdministerBeforeSubmitIgnoresAdminDescriptor: identical flow, but the
        // permission is never dropped. This proves the form submission itself is well-formed and
        // capable of changing the ADMINISTER-level descriptor.
        setupSecurity(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("user"));

        ManageRequiringConfig.get().setValue("manage-initial");
        AdminRequiringConfig.get().setValue("admin-initial");

        JenkinsRule.WebClient wc = webClient("user");
        HtmlPage page = wc.goTo("manage/appearance");
        HtmlForm form = page.getFormByName("config");

        String manageName = ManageRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        String adminName = AdminRequiringConfig.get().getDescriptor().getJsonSafeClassName();
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + manageName + "'] ~ * input[name='_.value']").setValue("manage-edited");
        page.<org.htmlunit.html.HtmlInput>querySelector("div[name='" + adminName + "'] ~ * input[name='_.value']").setValue("admin-edited");

        j.submit(form);

        assertEquals("manage-edited", ManageRequiringConfig.get().getValue());
        assertEquals("admin-edited", AdminRequiringConfig.get().getValue());
    }
}
