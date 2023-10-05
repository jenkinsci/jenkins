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

package hudson.model;

import static org.junit.Assert.assertEquals;

import hudson.Launcher;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPasswordInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestBuilder;

public class PasswordParameterDefinitionTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void defaultValueKeptSecret() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("p", "s3cr3t", "")));
        j.configRoundtrip(p);
        assertEquals("s3cr3t", ((PasswordParameterDefinition) p.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("p")).getDefaultValue());
    }

    @Issue("JENKINS-36476")
    @Test public void defaultValueAlwaysAvailable() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("admin").
            grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("dev"));
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("secret", "s3cr3t", "")));
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("I heard about a " + build.getEnvironment(listener).get("secret") + "!");
                return true;
            }
        });

        User admin = User.getById("admin", true);
        User dev = User.getById("dev", true);

        JenkinsRule.WebClient wc = j.createWebClient()
                // ParametersDefinitionProperty/index.jelly sends a 405 but really it is OK
                .withThrowExceptionOnFailingStatusCode(false);
        // Control case: admin can use default value.
        j.submit(wc.withBasicApiToken(admin).getPage(p, "build?delay=0sec").getFormByName("parameters"));
        j.waitUntilNoActivity();
        FreeStyleBuild b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        j.assertLogContains("I heard about a s3cr3t!", j.assertBuildStatusSuccess(b1));

        // Another control case: anyone can enter a different value.
        HtmlForm form = wc.withBasicApiToken(dev).getPage(p, "build?delay=0sec").getFormByName("parameters");
        ((HtmlElement) form.querySelector("button.hidden-password-update-btn")).click();
        HtmlPasswordInput input = form.getInputByName("value");
        input.setText("rumor");
        j.submit(form);
        j.waitUntilNoActivity();
        FreeStyleBuild b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        j.assertLogContains("I heard about a rumor!", j.assertBuildStatusSuccess(b2));

        // Test case: anyone can use default value.
        j.submit(wc.withBasicApiToken(dev).getPage(p, "build?delay=0sec").getFormByName("parameters"));
        j.waitUntilNoActivity();
        FreeStyleBuild b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        j.assertLogContains("I heard about a s3cr3t!", j.assertBuildStatusSuccess(b3));

        // Another control case: blank values.
        form = wc.withBasicApiToken(dev).getPage(p, "build?delay=0sec").getFormByName("parameters");
        ((HtmlElement) form.querySelector("button.hidden-password-update-btn")).click();
        input = form.getInputByName("value");
        input.setText("");
        j.submit(form);
        j.waitUntilNoActivity();
        FreeStyleBuild b4 = p.getLastBuild();
        assertEquals(4, b4.getNumber());
        j.assertLogContains("I heard about a !", j.assertBuildStatusSuccess(b4));
    }

}
