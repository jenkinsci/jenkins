/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.Arrays;
import java.util.Collections;

import org.acegisecurity.Authentication;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.Permission;
import jenkins.model.Jenkins;

/**
 * Tests for {@link AuthorizationStrategyOverrideConfiguration}
 */
public class AuthorizationStrategyOverrideConfigurationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Setups basic securities.
     * Sets up following users:
     * * admin     Administrator
     */
    @Before
    public void enableSecurity() throws Exception {
        // To use the configuration page in tests,
        // JenkinsRule#createDummySecurityRealm() isn't applicable.
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(
            false,      // allowSignup
            false,      // enableCaptcha
            null        // captchaSupport
        );
        realm.createAccount("admin", "admin");
        realm.createAccount("developer", "developer");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admin");
        j.jenkins.setAuthorizationStrategy(auth);
    }

    private void configRoundTripGlobalSecurity() throws Exception {
        j.submit(j.createWebClient().login("admin").goTo("configureSecurity").getFormByName("config"));
    }

    /**
     * AuthorizationStrategyOverride to test configurations.
     */
    public static class TestConfigureAuthorizationStrategyOverride extends AuthorizationStrategyOverride<Jenkins> {
        private final String field;

        @DataBoundConstructor
        public TestConfigureAuthorizationStrategyOverride(String field) {
            this.field = field;
        }

        public String getField() {
            return field;
        }

        @Override
        public Boolean hasPermission(Jenkins item, Authentication a, Permission p) {
            // do nothing
            return null;
        }

        @TestExtension
        public static class DescriptorImpl extends AuthorizationStrategyOverrideDescriptor {
        }

    }

    @Test
    public void testConfigurationDisabled() throws Exception {
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().clear();
        configRoundTripGlobalSecurity();
        assertEquals(
            Collections.emptyList(),
            AuthorizationStrategyOverrideConfiguration.get().getOverrides()
        );
    }

    @Test
    public void testConfigurationEnabled() throws Exception {
        TestConfigureAuthorizationStrategyOverride expect1 =
            new TestConfigureAuthorizationStrategyOverride("test value1");
        TestConfigureAuthorizationStrategyOverride expect2 =
            new TestConfigureAuthorizationStrategyOverride("test value2");
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().clear();
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().add(expect1);
        AuthorizationStrategyOverrideConfiguration.get().getOverrides().add(expect2);
        configRoundTripGlobalSecurity();
        j.assertEqualDataBoundBeans(
            Arrays.asList(expect1, expect2),
            AuthorizationStrategyOverrideConfiguration.get().getOverrides()
        );
        // assert they are newly configured ones.
        assertNotSame(expect1, AuthorizationStrategyOverrideConfiguration.get().getOverrides().get(0));
        assertNotSame(expect2, AuthorizationStrategyOverrideConfiguration.get().getOverrides().get(1));
        // ensure that is is saved
        j.assertEqualDataBoundBeans(
                Arrays.asList(expect1, expect2),
                new AuthorizationStrategyOverrideConfiguration().getOverrides()
            );
    }
}
