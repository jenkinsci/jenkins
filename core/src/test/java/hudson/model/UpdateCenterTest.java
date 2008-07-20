package hudson.model;

import junit.framework.TestCase;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;

/**
 * Quick test for {@link UpdateCenter}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenterTest extends TestCase {
    public void testData() throws IOException {
        // check if we have the internet connectivity. See HUDSON-2095
        try {
            new URL("https://hudson.dev.java.net/").openStream();
        } catch (IOException e) {
            System.out.println("Skipping this test. No internet connectivity");
            return;
        }

        URL url = new URL("https://hudson.dev.java.net/update-center.json?version=build");
        String jsonp = IOUtils.toString(url.openStream());
        String json = jsonp.substring(jsonp.indexOf('(')+1,jsonp.lastIndexOf(')'));

        UpdateCenter uc = new UpdateCenter();
        UpdateCenter.Data data = uc.new Data(JSONObject.fromObject(json));
        assertTrue(data.core.url.startsWith("https://hudson.dev.java.net/"));
        assertTrue(data.plugins.containsKey("rake"));
        System.out.println(data.core.url);
    }
}
