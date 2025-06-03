package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.DownloadService.Downloadable;
import hudson.tasks.Ant.AntInstaller;
import hudson.tasks.Maven;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.WithoutJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
class DownloadServiceTest {

    @WithoutJenkins // could have been in core/src/test/ but update-center.json was already in test/src/test/ (used by UpdateSiteTest)
    @Test
    void testLoadJSON() throws Exception {
        assertRoots("[list]", getClass().getResource("hudson.tasks.Maven.MavenInstaller.json")); // format used by most tools
        assertRoots("[data, version]", getClass().getResource("hudson.tools.JDKInstaller.json")); // anomalous format
        assertRoots("[connectionCheckUrl, core, id, plugins, signature, updateCenterVersion]", UpdateSiteTest.extract("update-center.json"));
    }

    private static void assertRoots(String expected, URL resource) throws Exception {
        assertNotNull(resource);
        JSONObject json = JSONObject.fromObject(DownloadService.loadJSON(resource));
        Set<String> keySet = json.keySet();
        assertEquals(expected, new TreeSet<>(keySet).toString());
    }

    @Test
    void testReduceFunctionWithMavenJsons() throws Exception {
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

    @Test
    void testReduceFunctionWithAntJsons() throws Exception {
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
        Downloadable downloadable = new AntInstaller.DescriptorImpl().createDownloadable();
        JSONObject reducedJson = downloadable.reduce(jsonObjectList);
        URL expectedResult = DownloadServiceTest.class.getResource("hudson.tasks.Ant.AntInstallerResult.json");
        JSONObject expectedResultJson = JSONObject.fromObject(DownloadService.loadJSON(expectedResult));
        assertEquals(reducedJson, expectedResultJson);
    }

    @Test
    void testReduceFunctionWithNotDefaultSchemaJsons() throws Exception {
        URL resource1 = DownloadServiceTest.class.getResource("hudson.plugins.cmake.CmakeInstaller1.json");
        URL resource2 = DownloadServiceTest.class.getResource("hudson.plugins.cmake.CmakeInstaller2.json");
        JSONObject json1 = JSONObject.fromObject(DownloadService.loadJSON(resource1));
        JSONObject json2 = JSONObject.fromObject(DownloadService.loadJSON(resource2));
        List<JSONObject> jsonObjectList = new ArrayList<>();
        jsonObjectList.add(json1);
        jsonObjectList.add(json2);
        Downloadable downloadable = new GenericDownloadFromUrlInstaller.DescriptorImpl().createDownloadable();
        JSONObject reducedJson = downloadable.reduce(jsonObjectList);
        URL expectedResult = DownloadServiceTest.class.getResource("hudson.plugins.cmake.CmakeInstallerResult.json");
        JSONObject expectedResultJson = JSONObject.fromObject(DownloadService.loadJSON(expectedResult));
        assertEquals(reducedJson, expectedResultJson);
    }

    private static class GenericDownloadFromUrlInstaller extends DownloadFromUrlInstaller {
        protected GenericDownloadFromUrlInstaller(String id) {
            super(id);
        }

        public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<Maven.MavenInstaller> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "";
            }

            @Override
            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return true;
            }
        }
    }
}
