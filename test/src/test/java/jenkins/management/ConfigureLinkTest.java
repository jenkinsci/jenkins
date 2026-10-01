/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

package jenkins.management;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.ManagementLink;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ConfigureLinkTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private void clearOtherMonitors() {
        ExtensionList<AdministrativeMonitor> extensionList = j.jenkins.getExtensionList(AdministrativeMonitor.class);
        extensionList.removeAll(extensionList.stream()
                .filter(m -> m.getClass().getEnclosingClass() != ConfigureLinkTest.class)
                .toList());
    }

    @Issue("#27230")
    @Test
    void badgeReturnsNullWhenNoMonitorsActive() {
        clearOtherMonitors();
        ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
        assertThat(link, notNullValue());
        assertThat(link.getBadge(), nullValue());
    }

    @Issue("#27230")
    @Test
    void badgeReturnsActiveCountWhenMonitorsActive() {
        clearOtherMonitors();
        ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
        assertThat(link, notNullValue());

        Badge badge = link.getBadge();
        assertThat(badge, notNullValue());
        assertThat(badge.getText(), is("1"));
        assertThat(badge.getSeverity(), is("warning"));
        assertThat(badge.getTooltip(), containsString("1 active system notification"));
    }

    @Issue("#27230")
    @Test
    void badgeReturnsDangerSeverityWhenSecurityMonitorActive() {
        clearOtherMonitors();
        ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
        assertThat(link, notNullValue());

        Badge badge = link.getBadge();
        assertThat(badge, notNullValue());
        assertThat(badge.getText(), is("2"));
        assertThat(badge.getSeverity(), is("danger"));
        assertThat(badge.getTooltip(), containsString("2 active system notifications"));
        assertThat(badge.getTooltip(), containsString("1 notification is security related"));
    }

    @Issue("#27230")
    @Test
    void badgeReturnsNullWhenMonitorDisabled() throws Exception {
        clearOtherMonitors();
        ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
        assertThat(link, notNullValue());

        DisabledTargetTestMonitor monitor = ExtensionList.lookupSingleton(DisabledTargetTestMonitor.class);
        monitor.disable(true);
        try {
            assertThat(link.getBadge(), nullValue());
        } finally {
            monitor.disable(false);
        }
    }

    @Issue("#27230")
    @Test
    void badgeReturnsNullWhenMonitorActivationFake() {
        clearOtherMonitors();
        ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
        assertThat(link, notNullValue());
        assertThat(link.getBadge(), nullValue());
    }

    @Issue("#27230")
    @Test
    void badgeReturnsNullWhenUserLacksPermission() {
        clearOtherMonitors();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("alice")
        );

        User alice = User.getById("alice", true);
        assertThat(alice, notNullValue());

        try (ACLContext ignored = ACL.as2(alice.impersonate2())) {
            ConfigureLink link = j.jenkins.getExtensionList(ManagementLink.class).get(ConfigureLink.class);
            assertThat(link, notNullValue());
            assertThat(link.getBadge(), nullValue());
        }
    }

    @TestExtension("badgeReturnsActiveCountWhenMonitorsActive")
    public static class ActiveTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "ActiveTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }

    @TestExtension("badgeReturnsDangerSeverityWhenSecurityMonitorActive")
    public static class SecurityTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "SecurityTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }

        @Override
        public boolean isSecurity() {
            return true;
        }
    }

    @TestExtension("badgeReturnsDangerSeverityWhenSecurityMonitorActive")
    public static class AnotherActiveTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "AnotherActiveTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }

    @TestExtension("badgeReturnsNullWhenMonitorDisabled")
    public static class DisabledTargetTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "DisabledTargetTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }

    @TestExtension("badgeReturnsNullWhenMonitorActivationFake")
    public static class FakeActivationTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "FakeActivationTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }

        @Override
        public boolean isActivationFake() {
            return true;
        }
    }

    @TestExtension("badgeReturnsNullWhenUserLacksPermission")
    public static class PermissionTestMonitor extends AdministrativeMonitor {
        @Override
        public String getDisplayName() {
            return "PermissionTestMonitor";
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }
}
