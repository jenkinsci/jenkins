package hudson;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerTest extends HudsonTestCase {
    /**
     * Manual submission form.
     */
    public void testUpload() throws Exception {
        HtmlPage page = new WebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = env.temporaryDirectoryAllocator.allocate();
        File plugin = new File(dir, "tasks.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/tasks.hpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        submit(f);

        assertTrue( new File(hudson.getRootDir(),"plugins/tasks.hpi").exists() );
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("tasks.hpi")
    public void testWithRecipe() throws Exception {
        assertNotNull(hudson.getPlugin("tasks"));
    }
}
