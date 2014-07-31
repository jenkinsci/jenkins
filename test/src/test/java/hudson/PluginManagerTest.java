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
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.filters.StringInputStream;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerTest extends HudsonTestCase {
    @Override
    protected void setUp() throws Exception {
        setPluginManager(null); // use a fresh instance
        super.setUp();
    }

    /**
     * Manual submission form.
     */
    public void testUploadJpi() throws Exception {
        HtmlPage page = new WebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = env.temporaryDirectoryAllocator.allocate();
        File plugin = new File(dir, "tasks.jpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/tasks.jpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        submit(f);

        assertTrue( new File(jenkins.getRootDir(),"plugins/tasks.jpi").exists() );
    }

    /**
     * Manual submission form.
     */
    public void testUploadHpi() throws Exception {
        HtmlPage page = new WebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = env.temporaryDirectoryAllocator.allocate();
        File plugin = new File(dir, "legacy.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/legacy.hpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        submit(f);

        // uploaded legacy plugins get renamed to *.jpi
        assertTrue( new File(jenkins.getRootDir(),"plugins/legacy.jpi").exists() );
    }
    
    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("tasks.jpi")
    public void testWithRecipeJpi() throws Exception {
        assertNotNull(jenkins.getPlugin("tasks"));
    }
    
    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("legacy.hpi")
    public void testWithRecipeHpi() throws Exception {
        assertNotNull(jenkins.getPlugin("legacy"));
    }

    /**
     * Makes sure that plugins can see Maven2 plugin that's refactored out in 1.296.
     */
    @WithPlugin("tasks.jpi")
    public void testOptionalMavenDependency() throws Exception {
        PluginWrapper.Dependency m2=null;
        PluginWrapper tasks = jenkins.getPluginManager().getPlugin("tasks");
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
    public void testUberClassLoaderIsAvailableDuringStart() {
        assertTrue(((PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart) jenkins.pluginManager).tested);
    }

    public class PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart extends LocalPluginManager {
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
    public void testUberClassLoaderDoesntUseContextClassLoader() throws Exception {
        Thread t = Thread.currentThread();

        URLClassLoader ucl = new URLClassLoader(new URL[0], jenkins.pluginManager.uberClassLoader);

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

    public void testInstallWithoutRestart() throws Exception {
        URL res = getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi");
        File f = new File(jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
        FileUtils.copyURLToFile(res, f);
        jenkins.pluginManager.dynamicLoad(f);

        Class c = jenkins.getPluginManager().uberClassLoader.loadClass("htmlpublisher.HtmlPublisher$DescriptorImpl");
        assertNotNull(jenkins.getDescriptorByType(c));
    }

    public void testPrevalidateConfig() throws Exception {
        PersistedList<UpdateSite> sites = jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/tasks-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());
        assertEquals(Collections.emptyList(), jenkins.getPluginManager().prevalidateConfig(new StringInputStream("<whatever><runant plugin=\"ant@1.1\"/></whatever>")));
        assertNull(jenkins.getPluginManager().getPlugin("tasks"));
        List<Future<UpdateCenterJob>> jobs = jenkins.getPluginManager().prevalidateConfig(new StringInputStream("<whatever><tasks plugin=\"tasks@2.23\"/></whatever>"));
        assertEquals(1, jobs.size());
        UpdateCenterJob job = jobs.get(0).get(); // blocks for completion
        assertEquals("InstallationJob", job.getType());
        UpdateCenter.InstallationJob ijob = (UpdateCenter.InstallationJob) job;
        assertEquals("tasks", ijob.plugin.name);
        assertNotNull(jenkins.getPluginManager().getPlugin("tasks"));
        // TODO restart scheduled (SuccessButRequiresRestart) after upgrade or Support-Dynamic-Loading: false
        // TODO dependencies installed or upgraded too
        // TODO required plugin installed but inactive
    }

}
