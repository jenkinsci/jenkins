package hudson.model.listeners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Saveable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * {@link SaveableListener} is told about an item's configuration however that configuration was written.
 */
@WithJenkins
class SaveableListenerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        ExtensionList.lookupSingleton(ConfigRecorder.class).changes.clear();
    }

    @Test
    void createdFromXml() {
        CLICommandInvoker.Result result = new CLICommandInvoker(j, "create-job")
                .withStdin(new ByteArrayInputStream(
                        "<project><description>from xml</description></project>".getBytes(StandardCharsets.UTF_8)))
                .invokeWithArgs("fromXml");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertNotNull(j.jenkins.getItem("fromXml"));

        assertThat(ExtensionList.lookupSingleton(ConfigRecorder.class).changes, hasItem("fromXml: from xml"));
    }

    @Test
    void copiedIntoAnotherFolder() throws Exception {
        FreeStyleProject source = j.createFreeStyleProject("source");
        source.setDescription("copied description");
        MockFolder folder = j.createFolder("folder");
        ConfigRecorder recorder = ExtensionList.lookupSingleton(ConfigRecorder.class);
        recorder.changes.clear();

        folder.copy(source, "copy");

        // Not only the empty project that is saved before the source's configuration is copied in.
        assertThat(recorder.changes, hasItem("folder/copy: copied description"));
    }

    @Test
    void createdFromScratchStillNotifies() throws Exception {
        j.createFreeStyleProject("scratch");
        assertThat(String.join("\n", ExtensionList.lookupSingleton(ConfigRecorder.class).changes), containsString("scratch: "));
    }

    @TestExtension
    public static class ConfigRecorder extends SaveableListener {
        final List<String> changes = new ArrayList<>();

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Item item) {
                String description = "";
                try {
                    String xml = file.asString();
                    int start = xml.indexOf("<description>");
                    if (start >= 0) {
                        description = xml.substring(start + "<description>".length(), xml.indexOf("</description>"));
                    }
                } catch (IOException e) {
                    description = "unreadable: " + e;
                }
                changes.add(item.getFullName() + ": " + description);
            }
        }
    }
}
