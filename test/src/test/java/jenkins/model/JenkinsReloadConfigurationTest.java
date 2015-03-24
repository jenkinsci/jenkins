package jenkins.model;

import static org.junit.Assert.assertEquals;

import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.User;
import hudson.tasks.Mailer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
    public void reloadMasterConfig() throws Exception {
        Node node = j.jenkins;
        node.setLabelString("oldLabel");

        modifyNode(node);

        assertEquals("newLabel", node.getLabelString());
    }

    @Test
    public void reloadSlaveConfig() throws Exception {
        Node node = j.createSlave("a_slave", "oldLabel", null);

        modifyNode(node);

        node = j.jenkins.getNode("a_slave");
        assertEquals("newLabel", node.getLabelString());
    }

    private void modifyNode(Node node) throws Exception {
        replace(node.getNodeName().equals("") ? "config.xml" : String.format("nodes/%s/config.xml",node.getNodeName()), "oldLabel", "newLabel");

        assertEquals("oldLabel", node.getLabelString());

        j.jenkins.reload();
    }

    @Test
    public void reloadUserConfig() throws Exception {
        User user = User.get("some_user", true, null);
        user.setFullName("oldName");
        user.save();

        replace("users/some_user/config.xml", "oldName", "newName");

        assertEquals("oldName", user.getFullName());

        User.reload();

        assertEquals("newName", user.getFullName());
    }

    @Test
    public void reloadUserConfigUsingGlobalReload() throws Exception {
        User user = User.get("some_user", true, null);
        user.setFullName("oldName");
        user.save();

        replace("users/some_user/config.xml", "oldName", "newName");

        assertEquals("oldName", user.getFullName());

        j.jenkins.reload();

        assertEquals("newName", user.getFullName());
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
        try {
            String oldConfig = Util.loadFile(configFile);

            String newConfig = oldConfig.replaceAll(search, replace);

            FileWriter fw = new FileWriter(configFile);
            fw.write(newConfig);
            fw.close();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }
}
