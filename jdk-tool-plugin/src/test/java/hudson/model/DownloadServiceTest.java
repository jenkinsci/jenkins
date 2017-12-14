package hudson.model;


import hudson.tools.JDKInstaller;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Kohsuke Kawaguchi
 */
public class DownloadServiceTest {

    @Test
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
