package org.jvnet.hudson.main;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import hudson.LocalPluginManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import jenkins.model.JenkinsLocationConfiguration;

public class UseRecipesWithJenkinsRuleTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    @LocalData
    public void testGetItemFromLocalData() {
        assertNotNull(rule.jenkins.getItem("testJob"));
    }

    @Test
    @WithPlugin("tasks.jpi")
    public void testWithPlugin() {
        assertNotNull(rule.jenkins.getPlugin("tasks"));
    }

    @Test
    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void testPresetData() throws Exception {
        WebClient wc = rule.createWebClient();
        wc.assertFails("loginError", SC_UNAUTHORIZED);
        // but not once the user logs in.
        verifyNotError(wc.login("alice"));
    }

    @Test
    @WithPluginManager(MyPluginManager.class)
    public void testWithPluginManager() {
        assertEquals(MyPluginManager.class, rule.jenkins.pluginManager.getClass());
    }

    @Test public void rightURL() throws Exception {
        assertEquals(rule.getURL(), new URL(JenkinsLocationConfiguration.get().getUrl()));
    }

    private void verifyNotError(WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("loginError");
        URL url = p.getWebResponse().getUrl();
        System.out.println(url);
        assertFalse(url.toExternalForm().contains("login"));
    }

    public static class MyPluginManager extends LocalPluginManager {
        public MyPluginManager(File rootDir) {
            super(rootDir);
        }
    }
}
