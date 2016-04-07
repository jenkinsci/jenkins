/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.PluginManager.UberClassLoader;
import hudson.model.Hudson;
import hudson.model.UpdateCenter;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import hudson.scm.SubversionSCM;
import hudson.util.FormValidation;
import hudson.util.PersistedList;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import jenkins.RestartRequiredException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.filters.StringInputStream;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerTest {

    @Rule public JenkinsRule r = PluginManagerUtil.newJenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Manual submission form.
     */
    @Test public void uploadJpi() throws Exception {
        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, "tasks.jpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/tasks.jpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        r.submit(f);

        assertTrue( new File(r.jenkins.getRootDir(),"plugins/tasks.jpi").exists() );
    }

    /**
     * Manual submission form.
     */
    @Test public void uploadHpi() throws Exception {
        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, "legacy.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/legacy.hpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        r.submit(f);

        // uploaded legacy plugins get renamed to *.jpi
        assertTrue( new File(r.jenkins.getRootDir(),"plugins/legacy.jpi").exists() );
    }
    
    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("tasks.jpi")
    @Test public void withRecipeJpi() throws Exception {
        assertNotNull(r.jenkins.getPlugin("tasks"));
    }
    
    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("legacy.hpi")
    @Test public void withRecipeHpi() throws Exception {
        assertNotNull(r.jenkins.getPlugin("legacy"));
    }

    /**
     * Makes sure that plugins can see Maven2 plugin that's refactored out in 1.296.
     */
    @WithPlugin("tasks.jpi")
    @Test public void optionalMavenDependency() throws Exception {
        PluginWrapper.Dependency m2=null;
        PluginWrapper tasks = r.jenkins.getPluginManager().getPlugin("tasks");
        for( PluginWrapper.Dependency d : tasks.getOptionalDependencies() ) {
            if(d.shortName.equals("maven-plugin")) {
                assertNull(m2);
                m2 = d;
            }
        }
        assertNotNull(m2);

        // this actually doesn't really test what we need, though, because
        // I thought test harness is loading the maven classes by itself.
        // TODO: write a separate test that tests the optional dependency loading
        tasks.classLoader.loadClass(hudson.maven.agent.AbortException.class.getName());
    }

    /**
     * Verifies that by the time {@link Plugin#start()} is called, uber classloader is fully functioning.
     * This is necessary as plugin start method can engage in XStream loading activities, and they should
     * resolve all the classes in the system (for example, a plugin X can define an extension point
     * other plugins implement, so when X loads its config it better sees all the implementations defined elsewhere)
     */
    @WithPlugin("tasks.jpi")
    @WithPluginManager(PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart.class)
    @Test public void uberClassLoaderIsAvailableDuringStart() {
        assertTrue(((PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart) r.jenkins.pluginManager).tested);
    }

    public static class PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart extends LocalPluginManager {
        boolean tested;

        public PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart(File rootDir) {
            super(rootDir);
        }

        @Override
        protected PluginStrategy createPluginStrategy() {
            return new ClassicPluginStrategy(this) {
                @Override
                public void startPlugin(PluginWrapper plugin) throws Exception {
                    tested = true;

                    // plugins should be already visible in the UberClassLoader
                    assertTrue(!activePlugins.isEmpty());

                    uberClassLoader.loadClass(SubversionSCM.class.getName());
                    uberClassLoader.loadClass("hudson.plugins.tasks.Messages");

                    super.startPlugin(plugin);
                }
            };
        }
    }


    /**
     * Makes sure that thread context classloader isn't used by {@link UberClassLoader}, or else
     * infinite cycle ensues.
     */
    @Url("http://jenkins.361315.n4.nabble.com/channel-example-and-plugin-classes-gives-ClassNotFoundException-td3756092.html")
    @Test public void uberClassLoaderDoesntUseContextClassLoader() throws Exception {
        Thread t = Thread.currentThread();

        URLClassLoader ucl = new URLClassLoader(new URL[0], r.jenkins.pluginManager.uberClassLoader);

        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(ucl);
        try {
            try {
                ucl.loadClass("No such class");
                fail();
            } catch (ClassNotFoundException e) {
                // as expected
            }

            ucl.loadClass(Hudson.class.getName());
        } finally {
            t.setContextClassLoader(old);
        }
    }

    @Test public void installWithoutRestart() throws Exception {
        URL res = getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi");
        File f = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
        FileUtils.copyURLToFile(res, f);
        r.jenkins.pluginManager.dynamicLoad(f);

        Class c = r.jenkins.getPluginManager().uberClassLoader.loadClass("htmlpublisher.HtmlPublisher$DescriptorImpl");
        assertNotNull(r.jenkins.getDescriptorByType(c));
    }

    @Test public void prevalidateConfig() throws Exception {
        PersistedList<UpdateSite> sites = r.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/tasks-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());
        assertEquals(Collections.emptyList(), r.jenkins.getPluginManager().prevalidateConfig(new StringInputStream("<whatever><runant plugin=\"ant@1.1\"/></whatever>")));
        assertNull(r.jenkins.getPluginManager().getPlugin("tasks"));
        List<Future<UpdateCenterJob>> jobs = r.jenkins.getPluginManager().prevalidateConfig(new StringInputStream("<whatever><tasks plugin=\"tasks@2.23\"/></whatever>"));
        assertEquals(1, jobs.size());
        UpdateCenterJob job = jobs.get(0).get(); // blocks for completion
        assertEquals("InstallationJob", job.getType());
        UpdateCenter.InstallationJob ijob = (UpdateCenter.InstallationJob) job;
        assertEquals("tasks", ijob.plugin.name);
        assertNotNull(r.jenkins.getPluginManager().getPlugin("tasks"));
        // TODO restart scheduled (SuccessButRequiresRestart) after upgrade or Support-Dynamic-Loading: false
        // TODO dependencies installed or upgraded too
        // TODO required plugin installed but inactive
    }

    // plugin "depender" optionally depends on plugin "dependee".
    // they are written like this:
    // org.jenkinsci.plugins.dependencytest.dependee:
    //   public class Dependee {
    //     public static String getValue() {
    //       return "dependee";
    //     }
    //   }
    //   
    //   public abstract class DependeeExtensionPoint implements ExtensionPoint {
    //   }
    //   
    // org.jenkinsci.plugins.dependencytest.depender:
    //   public class Depender {
    //     public static String getValue() {
    //       if (Jenkins.getInstance().getPlugin("dependee") != null) {
    //         return Dependee.getValue();
    //       }
    //       return "depender";
    //     }
    //   }
    //   
    //   @Extension(optional=true)
    //   public class DependerExtension extends DependeeExtensionPoint {
    //   }
    
    
    /**
     * call org.jenkinsci.plugins.dependencytest.depender.Depender.getValue().
     * 
     * @return
     * @throws Exception
     */
    private String callDependerValue() throws Exception {
        Class<?> c = r.jenkins.getPluginManager().uberClassLoader.loadClass("org.jenkinsci.plugins.dependencytest.depender.Depender");
        Method m = c.getMethod("getValue");
        return (String)m.invoke(null);
    }
    
    /**
     * Load "dependee" and then load "depender".
     * Asserts that "depender" can access to "dependee".
     * 
     * @throws Exception
     */
    @Test public void installDependingPluginWithoutRestart() throws Exception {
        // Load dependee.
        {
            dynamicLoad("dependee.hpi");
        }
        
        // before load depender, of course failed to call Depender.getValue()
        try {
            callDependerValue();
            fail();
        } catch (ClassNotFoundException _) {
        }
        
        // No extensions exist.
        assertTrue(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());
        
        // Load depender.
        {
            dynamicLoad("depender.hpi");
        }
        
        // depender successfully accesses to dependee.
        assertEquals("dependee", callDependerValue());
        
        // Extension in depender is loaded.
        assertFalse(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());
    }
    
    /**
     * Load "depender" and then load "dependee".
     * Asserts that "depender" can access to "dependee".
     * 
     * @throws Exception
     */
    @Issue("JENKINS-19976")
    @Test public void installDependedPluginWithoutRestart() throws Exception {
        // Load depender.
        {
            dynamicLoad("depender.hpi");
        }
        
        // before load dependee, depender does not access to dependee.
        assertEquals("depender", callDependerValue());
        
        // before load dependee, of course failed to list extensions for dependee.
        try {
            r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint");
            fail();
        } catch( ClassNotFoundException _ ){
        }
        
        // Load dependee.
        {
            dynamicLoad("dependee.hpi");
        }
        
        // (MUST) Not throws an exception
        // (SHOULD) depender successfully accesses to dependee.
        assertEquals("dependee", callDependerValue());
        
        // No extensions exist.
        // extensions in depender is not loaded.
        assertTrue(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());
    }

    @Issue("JENKINS-12753")
    @WithPlugin("tasks.jpi")
    @Test public void dynamicLoadRestartRequiredException() throws Exception {
        File jpi = new File(r.jenkins.getRootDir(), "plugins/tasks.jpi");
        assertTrue(jpi.isFile());
        FileUtils.touch(jpi);
        File timestamp = new File(r.jenkins.getRootDir(), "plugins/tasks/.timestamp2");
        assertTrue(timestamp.isFile());
        long lastMod = timestamp.lastModified();
        try {
            r.jenkins.getPluginManager().dynamicLoad(jpi);
            fail("should not have worked");
        } catch (RestartRequiredException x) {
            // good
        }
        assertEquals("should not have tried to delete & unpack", lastMod, timestamp.lastModified());
    }

    @WithPlugin("tasks.jpi")
    @Test public void pluginListJSONApi() throws IOException {
        JSONObject response = r.getJSON("pluginManager/plugins").getJSONObject();

        // Check that the basic API endpoint invocation works.
        assertEquals("ok", response.getString("status"));
        JSONArray data = response.getJSONArray("data");
        assertTrue(data.size() > 0);

        // Check that there was some data in the response and that the first entry
        // at least had some of the expected fields.
        JSONObject pluginInfo = data.getJSONObject(0);
        assertTrue(pluginInfo.getString("name") != null);
        assertTrue(pluginInfo.getString("title") != null);
        assertTrue(pluginInfo.getString("dependencies") != null);
    }

    private void dynamicLoad(String plugin) throws IOException, InterruptedException, RestartRequiredException {
        PluginManagerUtil.dynamicLoad(plugin, r.jenkins);
    }

    @Test public void uploadDependencyResolution() throws Exception {
        PersistedList<UpdateSite> sites = r.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/upload-test-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);

        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());

        // neither of the following plugins should be installed
        assertNull(r.jenkins.getPluginManager().getPlugin("Parameterized-Remote-Trigger"));
        assertNull(r.jenkins.getPluginManager().getPlugin("token-macro"));

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, "Parameterized-Remote-Trigger.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/Parameterized-Remote-Trigger.hpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        r.submit(f);

        assertTrue(r.jenkins.getUpdateCenter().getJobs().size() > 0);

        // wait for all the download jobs to complete
        boolean done = true;
        do {
            Thread.sleep(100);
            for(UpdateCenterJob job : r.jenkins.getUpdateCenter().getJobs()) {
                if(job instanceof UpdateCenter.DownloadJob) {
                    done &= ((UpdateCenter.DownloadJob)job).status.isSuccess();
                }
            }
        } while(!done);

        // the files get renamed to .jpi
        assertTrue( new File(r.jenkins.getRootDir(),"plugins/Parameterized-Remote-Trigger.jpi").exists() );
        assertTrue( new File(r.jenkins.getRootDir(),"plugins/token-macro.jpi").exists() );

        // now the other plugins should have been found as dependencies and downloaded
        assertNotNull(r.jenkins.getPluginManager().getPlugin("Parameterized-Remote-Trigger"));
        assertNotNull(r.jenkins.getPluginManager().getPlugin("token-macro"));
    }
}
