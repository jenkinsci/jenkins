package hudson.model;

import hudson.model.DownloadService.Downloadable;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hudson.tasks.Maven;
import hudson.tools.JDKInstaller;
import jenkins.model.DownloadSettings;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.WithoutJenkins;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Kohsuke Kawaguchi
 */
public class DownloadServiceTest extends HudsonTestCase {
    private Downloadable job;

    /**
     * Makes sure that JavaScript on the client side for handling submission works.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (jenkins == null) {
            return;
        }
        // this object receives the submission.
        // to bypass the URL restriction, we'll trigger downloadService.download ourselves
        job = new Downloadable("test", "UNUSED");
        Downloadable.all().add(job);
        DownloadSettings.get().setUseBrowser(true);
    }

    @Issue("JENKINS-5536")
    public void testPost() throws Exception {
        // we do not save with signature for toolInstallers,
        //neither we check it in the getData method.

        createWebClient().goTo("/self/testPost");
        JSONObject d = job.getData();
        assertEquals(hashCode(),d.getInt("hello"));

        // TODO: test with a signature
    }

    /**
     * This is where the browser should hit to retrieve data.
     */
    public void doData(StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/javascript");
        rsp.getWriter().println("downloadService.post('test',{'hello':"+hashCode()+"})");
    }

    @WithoutJenkins // could have been in core/src/test/ but update-center.json was already in test/src/test/ (used by UpdateSiteTest)
    public void testLoadJSON() throws Exception {
        assertRoots("[list]", "hudson.tasks.Maven.MavenInstaller.json"); // format used by most tools
        assertRoots("[data, version]", "hudson.tools.JDKInstaller.json"); // anomalous format
        assertRoots("[connectionCheckUrl, core, id, plugins, signature, updateCenterVersion]", "update-center.json");
    }

    private static void assertRoots(String expected, String file) throws Exception {
        URL resource = DownloadServiceTest.class.getResource(file);
        assertNotNull(file, resource);
        JSONObject json = JSONObject.fromObject(DownloadService.loadJSON(resource));
        @SuppressWarnings("unchecked") Set<String> keySet = json.keySet();
        assertEquals(expected, new TreeSet<String>(keySet).toString());
    }

    public void testReduceFunctionWithMavenJsons() throws Exception {
        URL resource1 = DownloadServiceTest.class.getResource("hudson.tasks.Maven.MavenInstaller1.json");
        URL resource2 = DownloadServiceTest.class.getResource("hudson.tasks.Maven.MavenInstaller2.json");
        URL resource3 = DownloadServiceTest.class.getResource("hudson.tasks.Maven.MavenInstaller3.json");
        JSONObject json1 = JSONObject.fromObject(DownloadService.loadJSON(resource1));
        JSONObject json2 = JSONObject.fromObject(DownloadService.loadJSON(resource2));
        JSONObject json3 = JSONObject.fromObject(DownloadService.loadJSON(resource3));
        List<JSONObject> jsonObjectList = new ArrayList<>();
        jsonObjectList.add(json1);
        jsonObjectList.add(json2);
        jsonObjectList.add(json3);
        Downloadable downloadable = new Maven.MavenInstaller.DescriptorImpl().createDownloadable();
        JSONObject reducedJson = downloadable.reduce(jsonObjectList);
        URL expectedResult = DownloadServiceTest.class.getResource("hudson.tasks.Maven.MavenInstallerResult.json");
        JSONObject expectedResultJson = JSONObject.fromObject(DownloadService.loadJSON(expectedResult));
        assertEquals(reducedJson, expectedResultJson);
    }

    public void testReduceFunctionWithAntJsons() throws Exception {
        URL resource1 = DownloadServiceTest.class.getResource("hudson.tasks.Ant.AntInstaller1.json");
        URL resource2 = DownloadServiceTest.class.getResource("hudson.tasks.Ant.AntInstaller2.json");
        URL resource3 = DownloadServiceTest.class.getResource("hudson.tasks.Ant.AntInstaller3.json");
        JSONObject json1 = JSONObject.fromObject(DownloadService.loadJSON(resource1));
        JSONObject json2 = JSONObject.fromObject(DownloadService.loadJSON(resource2));
        JSONObject json3 = JSONObject.fromObject(DownloadService.loadJSON(resource3));
        List<JSONObject> jsonObjectList = new ArrayList<>();
        jsonObjectList.add(json1);
        jsonObjectList.add(json2);
        jsonObjectList.add(json3);
        Downloadable downloadable = new hudson.tasks.Ant.AntInstaller.DescriptorImpl().createDownloadable();
        JSONObject reducedJson = downloadable.reduce(jsonObjectList);
        URL expectedResult = DownloadServiceTest.class.getResource("hudson.tasks.Ant.AntInstallerResult.json");
        JSONObject expectedResultJson = JSONObject.fromObject(DownloadService.loadJSON(expectedResult));
        assertEquals(reducedJson, expectedResultJson);
    }

    public void testReduceFunctionWithJDKJsons() throws Exception {
        URL resource1 = DownloadServiceTest.class.getResource("hudson.tools.JDKInstaller1.json");
        URL resource2 = DownloadServiceTest.class.getResource("hudson.tools.JDKInstaller2.json");
        URL resource3 = DownloadServiceTest.class.getResource("hudson.tools.JDKInstaller3.json");
        JSONObject json1 = JSONObject.fromObject(DownloadService.loadJSON(resource1));
        JSONObject json2 = JSONObject.fromObject(DownloadService.loadJSON(resource2));
        JSONObject json3 = JSONObject.fromObject(DownloadService.loadJSON(resource3));
        List<JSONObject> jsonObjectList = new ArrayList<>();
        jsonObjectList.add(json1);
        jsonObjectList.add(json2);
        jsonObjectList.add(json3);
        JDKInstaller.JDKList downloadable = new JDKInstaller.JDKList();
        JSONObject reducedJson = downloadable.reduce(jsonObjectList);
        URL expectedResult = DownloadServiceTest.class.getResource("hudson.tools.JDKInstallerResult.json");
        JSONObject expectedResultJson = JSONObject.fromObject(DownloadService.loadJSON(expectedResult));
        assertEquals(reducedJson, expectedResultJson);
    }
}
