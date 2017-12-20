package hudson.model;

/**
 * Created by lvotypko on 12/20/17.
 */

import hudson.util.VersionNumber;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by lvotypko on 12/14/17.
 */
public class UpdateCenterMultiplePluginVersionsTest {

    @Rule
    public final JenkinsRule j = new UpdateCenterCustomTest.CustomUpdateCenterRule(UpdateCenterMoreVersionsOfTheSamePlugin.class);

    @LocalData
    @Test
    public void testMoreVersionOfTheSameDependency() throws Exception{
        File file = new File(j.jenkins.getRootDir().getAbsolutePath() + "/update/site1.json");
        UpdateSite site1 = new UpdateSite("site1", file.toURI().toURL().toString());
        j.jenkins.getUpdateCenter().getSites().add(site1);
        UpdateCenterMoreVersionsOfTheSamePlugin center  = (UpdateCenterMoreVersionsOfTheSamePlugin) j.jenkins.getUpdateCenter();
        UpdateSite.Plugin p = j.jenkins.getUpdateCenter().getPlugin("subversion-fake");
        center.highest = false;
        Set<UpdateSite.Plugin> dependencies = p.getAllDependencies();
        for(UpdateSite.Plugin dependency: dependencies){
            if(dependency.name.equals("credentials-fake")){
                assertEquals("High version should be added.", "1.21", dependency.version);
                return;
            }
        }
        fail("No credentials-fake plugin was found.");
    }

    public static class UpdateCenterMoreVersionsOfTheSamePlugin extends UpdateCenter {

        public UpdateCenterMoreVersionsOfTheSamePlugin(){

        }

        public UpdateCenterMoreVersionsOfTheSamePlugin(UpdateCenterConfiguration config) {

        }
        private boolean highest = false;

        public @CheckForNull
        UpdateSite.Plugin getPlugin(String artifactId) {
            UpdateSite.Plugin p = null;
            for (UpdateSite s : getSites()) {
                UpdateSite.Plugin sitePlugin = s.getPlugin(artifactId);
                if (sitePlugin!=null) {
                    if(p==null){
                        p=sitePlugin;
                    }
                    else{
                        if(highest && new VersionNumber(sitePlugin.version).isNewerThan(new VersionNumber(p.version))){
                            p = sitePlugin;

                        }
                        if(!highest && new VersionNumber(sitePlugin.version).isOlderThan(new VersionNumber(p.version))){
                            p = sitePlugin;

                        }
                    }
                }
            }

            if(artifactId.equals("credentials-fake")) {
                //switch
                highest = !highest;
            }
            return p;
        }
    }
}

