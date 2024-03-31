package jenkins.model;

import static org.junit.Assert.assertEquals;

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
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Ensure direct configuration change on disk is reflected after reload.
 *
 * @author ogondza
 */
public class JenkinsReloadConfigurationTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void reloadBuiltinNodeConfig() throws Exception {
        Node node = j.jenkins;
        node.setLabelString("oldLabel");

        modifyNode(node);

        assertEquals("newLabel", node.getLabelString());
    }

    @Test
    public void reloadAgentConfig() throws Exception {
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
    public void reloadUserConfigUsingGlobalReload() throws Exception {
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
    public void reloadJobConfig() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("a_project");
        project.setDescription("oldDescription");

        replace("jobs/a_project/config.xml", "oldDescription", "newDescription");

        assertEquals("oldDescription", project.getDescription());

        j.jenkins.reload();

        project = j.jenkins.getItem("a_project", j.jenkins, FreeStyleProject.class);
        assertEquals("newDescription", project.getDescription());
    }

    @Test
    public void reloadViewConfig() throws Exception {
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
    public void reloadDescriptorConfig() {
        Mailer.DescriptorImpl desc = mailerDescriptor();
        desc.setDefaultSuffix("@oldSuffix");
        desc.save();

        replace("hudson.tasks.Mailer.xml", "@oldSuffix", "@newSuffix");

        assertEquals("@oldSuffix", desc.getDefaultSuffix());

        desc.load();

        assertEquals("@newSuffix", desc.getDefaultSuffix());
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
