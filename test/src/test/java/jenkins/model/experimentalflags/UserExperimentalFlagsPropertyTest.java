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

package jenkins.model.experimentalflags;

import static org.junit.Assert.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.model.User;
import java.util.HashMap;
import java.util.Map;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class UserExperimentalFlagsPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAnonymous() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage p = wc.goTo("self/withFlags");

        // No user => default value
        assertEquals("true", p.getElementById("test1Flag").getTextContent());
        assertEquals("false", p.getElementById("test2Flag").getTextContent());
        assertEquals("false", p.getElementById("test3Flag").getTextContent());
    }

    @Test
    public void testWithoutProperty() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");

        JenkinsRule.WebClient wc = j.createWebClient()
                .withBasicCredentials("user");

        HtmlPage p = wc.goTo("self/withFlags");

        assertEquals("true", p.getElementById("test1Flag").getTextContent());
        assertEquals("false", p.getElementById("test2Flag").getTextContent());
        assertEquals("false", p.getElementById("test3Flag").getTextContent());
    }

    @Test
    public void testPropertyWithDefault() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");
        UserExperimentalFlagsProperty property = new UserExperimentalFlagsProperty();
        user.addProperty(property);

        JenkinsRule.WebClient wc = j.createWebClient()
                .withBasicCredentials("user");

        HtmlPage p = wc.goTo("self/withFlags");

        // test1 has a different default
        assertEquals("true", p.getElementById("test1Flag").getTextContent());
        // test2 has an overloaded default with same as default value
        assertEquals("false", p.getElementById("test2Flag").getTextContent());
        assertEquals("false", p.getElementById("test3Flag").getTextContent());
    }

    @Test
    public void testPropertyWithValues() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");
        Map<String, String> flags = new HashMap<>();
        flags.put("test1.flag", "false");
        flags.put("test2.flag", "true");
        UserExperimentalFlagsProperty property = new UserExperimentalFlagsProperty(flags);
        user.addProperty(property);

        JenkinsRule.WebClient wc = j.createWebClient()
                .withBasicCredentials("user");

        HtmlPage p = wc.goTo("self/withFlags");

        assertEquals("false", p.getElementById("test1Flag").getTextContent());
        assertEquals("true", p.getElementById("test2Flag").getTextContent());
    }

    @Test
    public void testPropertyWithNull() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.getOrCreateByIdOrFullName("user");
        Map<String, String> flags = new HashMap<>();
        flags.put("test1.flag", null);
        flags.put("test2.flag", null);
        UserExperimentalFlagsProperty property = new UserExperimentalFlagsProperty(flags);
        user.addProperty(property);

        JenkinsRule.WebClient wc = j.createWebClient()
                .withBasicCredentials("user");

        HtmlPage p = wc.goTo("self/withFlags");

        // Using their default
        assertEquals("true", p.getElementById("test1Flag").getTextContent());
        assertEquals("false", p.getElementById("test2Flag").getTextContent());
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "self";
        }
    }

    @TestExtension
    public static final class Test1UserExperimentalFlag extends BooleanUserExperimentalFlag {
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

    @TestExtension
    public static final class Test2UserExperimentalFlag extends BooleanUserExperimentalFlag {
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

    @TestExtension
    public static final class Test3UserExperimentalFlag extends BooleanUserExperimentalFlag {
        public Test3UserExperimentalFlag() {
            super("test3.flag");
        }

        @Override
        public String getDisplayName() {
            return "Test3";
        }

        @Override
        public @Nullable String getShortDescription() {
            return "";
        }
    }
}
