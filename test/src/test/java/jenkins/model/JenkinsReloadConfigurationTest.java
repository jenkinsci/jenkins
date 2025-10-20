package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Ensure direct configuration change on disk is reflected after reload.
 *
 * @author ogondza
 */
@WithJenkins
class JenkinsReloadConfigurationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void reloadBuiltinNodeConfig() throws Exception {
        Node node = j.jenkins;
        node.setLabelString("oldLabel");

        modifyNode(node);

        assertEquals("newLabel", node.getLabelString());
    }

    @Test
    void reloadAgentConfig() throws Exception {
        Node node = j.createSlave("an_agent", "oldLabel", null);

        modifyNode(node);

        node = j.jenkins.getNode("an_agent");
        assertEquals("newLabel", node.getLabelString());
    }

    private void modifyNode(Node node) throws Exception {
        replace(node.getNodeName().isEmpty() ? "config.xml" : String.format("nodes/%s/config.xml", node.getNodeName()), "oldLabel", "newLabel");

        assertEquals("oldLabel", node.getLabelString());

        j.jenkins.reload();
    }

    @Test
    void reloadUserConfigUsingGlobalReload() throws Exception {
        String originalName = "oldName";
        String temporaryName = "newName";
        {
        User user = User.get("some_user", true, null);
        user.setFullName(originalName);
        user.save();
        assertEquals(originalName, user.getFullName());

        user.setFullName(temporaryName);
        assertEquals(temporaryName, user.getFullName());
        }
        j.jenkins.reload();
        {
            assertEquals(originalName, User.getById("some_user", false).getFullName());
        }
    }

    @Test
    void reloadJobConfig() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("a_project");
        project.setDescription("oldDescription");

        replace("jobs/a_project/config.xml", "oldDescription", "newDescription");

        assertEquals("oldDescription", project.getDescription());

        j.jenkins.reload();

        project = j.jenkins.getItem("a_project", j.jenkins, FreeStyleProject.class);
        assertEquals("newDescription", project.getDescription());
    }

    @Test
    void reloadViewConfig() throws Exception {
        ListView view = new ListView("a_view");
        j.jenkins.addView(view);

        view.setIncludeRegex("oldIncludeRegex");
        view.save();

        replace("config.xml", "oldIncludeRegex", "newIncludeRegex");

        assertEquals("oldIncludeRegex", view.getIncludeRegex());

        j.jenkins.reload();

        view = (ListView) j.jenkins.getView("a_view");
        assertEquals("newIncludeRegex", view.getIncludeRegex());
    }

    @Test
    void reloadDescriptorConfig() {
        Mailer.DescriptorImpl desc = mailerDescriptor();
        desc.setDefaultSuffix("@oldSuffix");
        desc.save();

        replace("hudson.tasks.Mailer.xml", "@oldSuffix", "@newSuffix");

        assertEquals("@oldSuffix", desc.getDefaultSuffix());

        desc.load();

        assertEquals("@newSuffix", desc.getDefaultSuffix());
    }

    @Test
    void loadExecutorsConfig() throws Exception {
        assertThat(j.jenkins.getNumExecutors(), is(2));
        assertThat(j.jenkins.toComputer().getNumExecutors(), is(2));
        replace("config.xml", "<numExecutors>2</numExecutors>", "<numExecutors>0</numExecutors>");
        j.jenkins.load();
        assertThat(j.jenkins.getNumExecutors(), is(0));
        assertThat(j.jenkins.toComputer().getNumExecutors(), is(0));
    }

    private Mailer.DescriptorImpl mailerDescriptor() {
        return j.jenkins.getExtensionList(Mailer.DescriptorImpl.class).get(0);
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
