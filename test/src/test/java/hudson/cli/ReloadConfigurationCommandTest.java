/*
 * The MIT License
 *
 * Copyright 2016 Red Hat, Inc.
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

package hudson.cli;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.User;
import hudson.tasks.Mailer;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class ReloadConfigurationCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        ReloadConfigurationCommand cmd = new ReloadConfigurationCommand();

        command = new CLICommandInvoker(j, cmd).asUser("user");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toAuthenticated()
                .grant(Jenkins.MANAGE).everywhere().toAuthenticated()
        );
    }

    @Test
    void reloadConfigurationShouldFailWithoutAdministerPermission() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command.invoke();

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("user is missing the Overall/Administer permission"));
    }

    @Test
    void reloadMasterConfig() throws Exception {
        Node node = j.jenkins;
        node.setLabelString("oldLabel");

        modifyNode(node);

        assertThat(node.getLabelString(), equalTo("newLabel"));
    }

    @Test
    void reloadSlaveConfig() throws Exception {
        Node node = j.createSlave("a_slave", "oldLabel", null);

        modifyNode(node);

        node = j.jenkins.getNode("a_slave");
        assertThat(node.getLabelString(), equalTo("newLabel"));
    }

    private void modifyNode(Node node) {
        replace(node.getNodeName().isEmpty() ? "config.xml" : String.format("nodes/%s/config.xml", node.getNodeName()), "oldLabel", "newLabel");

        assertThat(node.getLabelString(), equalTo("oldLabel"));

        reloadJenkinsConfigurationViaCliAndWait();
    }

    @Test
    void reloadUserConfig() throws Exception {
        String originalName = "oldName";
        String temporaryName = "newName";
        {
        User user = User.get("some_user", true, null);
        user.setFullName(originalName);
        user.save();
        assertThat(user.getFullName(), equalTo(originalName));

        user.setFullName(temporaryName);
        assertThat(user.getFullName(), equalTo(temporaryName));
        }
        reloadJenkinsConfigurationViaCliAndWait();
        {
        User user = User.getById("some_user", false);
        assertThat(user.getFullName(), equalTo(originalName));
        }
    }

    @Test
    void reloadJobConfig() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("a_project");
        project.setDescription("oldDescription");

        replace("jobs/a_project/config.xml", "oldDescription", "newDescription");

        assertThat(project.getDescription(), equalTo("oldDescription"));

        reloadJenkinsConfigurationViaCliAndWait();

        project = j.jenkins.getItem("a_project", j.jenkins, FreeStyleProject.class);
        assertThat(project.getDescription(), equalTo("newDescription"));
    }

    @Test
    void reloadViewConfig() throws Exception {
        ListView view = new ListView("a_view");
        j.jenkins.addView(view);

        view.setIncludeRegex("oldIncludeRegex");
        view.save();

        replace("config.xml", "oldIncludeRegex", "newIncludeRegex");

        assertThat(view.getIncludeRegex(), equalTo("oldIncludeRegex"));

        reloadJenkinsConfigurationViaCliAndWait();

        view = (ListView) j.jenkins.getView("a_view");
        assertThat(view.getIncludeRegex(), equalTo("newIncludeRegex"));
    }

    @Disabled // Until fixed JENKINS-8217
    @Test
    void reloadDescriptorConfig() {
        Mailer.DescriptorImpl desc = j.jenkins.getExtensionList(Mailer.DescriptorImpl.class).get(0);
        desc.setDefaultSuffix("@oldSuffix");
        desc.save();

        replace("hudson.tasks.Mailer.xml", "@oldSuffix", "@newSuffix");

        assertThat(desc.getDefaultSuffix(), equalTo("@oldSuffix"));

        reloadJenkinsConfigurationViaCliAndWait();

        assertThat(desc.getDefaultSuffix(), equalTo("@newSuffix"));
    }

    private void reloadJenkinsConfigurationViaCliAndWait() {
        final CLICommandInvoker.Result result = command.invoke();

        assertThat(result, succeededSilently());
    }

    private void replace(String path, String search, String replace) {
        File configFile = new File(j.jenkins.getRootDir(), path);

        String oldConfig;
        try {
            oldConfig = Util.loadFile(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String newConfig = oldConfig.replaceAll(search, replace);

        try (Writer writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(newConfig);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
