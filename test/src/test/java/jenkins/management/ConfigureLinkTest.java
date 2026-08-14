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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import hudson.model.ManagementLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
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
}
