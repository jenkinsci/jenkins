/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
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

package lib.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.model.User;
import java.util.HashMap;
import java.util.Map;
import jenkins.model.experimentalflags.BooleanUserExperimentalFlag;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UserExperimentalFlagTest {
    private static final String VALID_FLAG_CLASS = "lib.layout.UserExperimentalFlagTest$Test1UserExperimentalFlag";
    private static final String NON_REGISTERED_FLAG_CLASS = "lib.layout.UserExperimentalFlagTest$Test2UserExperimentalFlag";
    private static final String UNRELATED_CLASS = "lib.layout.UserExperimentalFlagTest";
    private static final String NON_EXISTING_FLAG_CLASS = "nonExisting";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testNonExistingClass() throws Exception {
        assertFlagUsage(NON_EXISTING_FLAG_CLASS, "", null);
    }

    @Test
    void testNonFlagClass() throws Exception {
        assertFlagUsage(UNRELATED_CLASS, "", null);
    }

    @Test
    void testExistingClassButNotRegisteredFlag() throws Exception {
        // No @Extension annotation
        assertFlagUsage(NON_REGISTERED_FLAG_CLASS, "", null);
    }

    @Test
    void testExistingFlagButAnonymousUser() throws Exception {
        // default value is true
        assertFlagUsage(VALID_FLAG_CLASS, "true", null);
    }

    @Test
    void testPropertyWithValues() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");
        Map<String, String> flags = new HashMap<>();
        flags.put("test1.flag", "false");
        UserExperimentalFlagsProperty property = new UserExperimentalFlagsProperty(flags);
        user.addProperty(property);

        assertFlagUsage(VALID_FLAG_CLASS, "false", user);
    }

    @Test
    void testPropertyWithNull() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");
        Map<String, String> flags = new HashMap<>();
        flags.put("test1.flag", null);
        UserExperimentalFlagsProperty property = new UserExperimentalFlagsProperty(flags);
        user.addProperty(property);

        // default value is true
        assertFlagUsage(VALID_FLAG_CLASS, "true", user);
    }

    private void assertFlagUsage(String flagClassName, String expectedValue, User user) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.getExtensionList(RootAction.class).get(RootActionImpl.class).setFlagClassName(flagClassName);

        JenkinsRule.WebClient wc;
        if (user == null) {
            wc = j.createWebClient();
        } else {
            wc = j.createWebClient()
                    .withBasicCredentials(user.getId());
        }

        HtmlPage p = wc.goTo("self/tag");
        String actualResult = p.getElementById("result").getTextContent();
        assertEquals(expectedValue, actualResult);
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        private String flagClassName;

        public String getFlagClassName() {
            return flagClassName;
        }

        public void setFlagClassName(String flagClassName) {
            this.flagClassName = flagClassName;
        }

        @Override
        public String getUrlName() {
            return "self";
        }
    }

    @TestExtension
    public static final class Test1UserExperimentalFlag extends BooleanUserExperimentalFlag {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public Test1UserExperimentalFlag() {
            super("test1.flag");
        }

        @Override
        public @NonNull Boolean getDefaultValue() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Test1";
        }

        @Override
        public @Nullable String getShortDescription() {
            return "";
        }
    }

    // Especially not registered extension
    public static final class Test2UserExperimentalFlag extends BooleanUserExperimentalFlag {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public Test2UserExperimentalFlag() {
            super("test2.flag");
        }

        @Override
        public @NonNull Boolean getDefaultValue() {
            // same as default, but for test coverage
            return false;
        }

        @Override
        public String getDisplayName() {
            return "Test2";
        }

        @Override
        public @Nullable String getShortDescription() {
            return "";
        }
    }
}
