package hudson;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.servlet.ServletContext;

import org.jvnet.hudson.test.HudsonTestCase;
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
