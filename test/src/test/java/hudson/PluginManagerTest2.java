package hudson;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletContext;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.FileUtils;

public class PluginManagerTest2 extends HudsonTestCase {
    @Override
    protected void setUp() throws Exception {
        setPluginManager(null); // use a fresh instance
        super.setUp();
    }

    private ServletContext servletContext;

    // need to keep the ServletContext to use in the actual test
    protected ServletContext createWebServer() throws Exception {
        servletContext=super.createWebServer();
        return servletContext;
    }

    @WithPlugin("tasks.jpi")
    public void testPinned() throws Exception {
        PluginWrapper tasks = jenkins.getPluginManager().getPlugin("tasks");
        assertFalse("tasks shouldn't be bundled",tasks.isBundled());
        assertFalse("tasks shouldn't be pinned before update",tasks.isPinned());
        uploadPlugin("tasks.jpi", false);
        assertFalse("tasks shouldn't be pinned after update",tasks.isPinned());

        PluginWrapper cvs = jenkins.getPluginManager().getPlugin("cvs");
        assertTrue("cvs should be bundled",cvs.isBundled());
        assertFalse("cvs shouldn't be pinned before update",cvs.isPinned());
        uploadPlugin("cvs.hpi", true);
        assertTrue("cvs should be pinned after update",cvs.isPinned());
    }

    private void uploadPlugin(String pluginName, boolean useServerRoot) throws IOException, SAXException, Exception {
        HtmlPage page = new WebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File plugin;
        if(useServerRoot) {
            String pluginsPath=servletContext.getRealPath("WEB-INF/plugins");
            plugin = new File(pluginsPath+"/"+pluginName);
       } else {
            File dir = env.temporaryDirectoryAllocator.allocate();
            plugin = new File(dir, pluginName);
            URL resource = getClass().getClassLoader().getResource("plugins/"+pluginName);
            FileUtils.copyURLToFile(resource,plugin);
        }
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        submit(f);
    }

}
