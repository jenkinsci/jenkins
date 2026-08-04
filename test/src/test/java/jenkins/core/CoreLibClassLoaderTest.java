package jenkins.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.UpdateSite;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@For(CoreLibClassLoader.class)
@WithJenkins
class CoreLibClassLoaderTest {

    @Test
    void htmlSanitizerIsAvailableToCore(JenkinsRule j) throws Exception {
        // Core can load PluginExcerptSanitizer implementations via Jenkins.getCoreLibrary(...)
        PluginExcerptSanitizer sanitizer = j.jenkins.getCoreLibrary(PluginExcerptSanitizer.class);
        assertNotNull(sanitizer, "PluginExcerptSanitizer implementation should be available");

        // Can invoke the sanitization method
        String result = sanitizer.sanitize("<strong>Safe content</strong><script>alert('XSS')</script>");
        assertThat(result, is("<strong>Safe content</strong>"));
    }

    @Test
    void updateSitePluginExcerptIsSanitized(JenkinsRule j) {
        // Create a mock plugin with potentially malicious HTML in excerpt
        JSONObject pluginJson = new JSONObject();
        pluginJson.put("name", "test-plugin");
        pluginJson.put("version", "1.0");
        pluginJson.put("url", "http://example.com/test.hpi");
        pluginJson.put("excerpt", "<p>This is a <b>safe</b> excerpt</p><script>alert('XSS')</script><iframe src='evil.com'></iframe>");
        pluginJson.put("dependencies", new net.sf.json.JSONArray());

        UpdateSite site = j.jenkins.getUpdateCenter().getSites().get(0);
        UpdateSite.Plugin plugin = site.new Plugin("test", pluginJson);

        // <p> block, script, and iframe are removed
        assertThat(plugin.excerpt, is("This is a <b>safe</b> excerpt"));
    }

    @Test
    void sanitizationPreservesAllowedHtml(JenkinsRule j) {
        JSONObject pluginJson = new JSONObject();
        pluginJson.put("name", "test-plugin");
        pluginJson.put("version", "1.0");
        pluginJson.put("url", "http://example.com/test.hpi");
        pluginJson.put("excerpt", "<p>Paragraph with <b>bold</b>, <i>italic</i>, <em>emphasis</em>, <strong>strong</strong>, <code>code</code>, and <a href=\"https://jenkins.io\">links</a></p><ul><li>Item 1</li><li>Item 2</li></ul>");
        pluginJson.put("dependencies", new net.sf.json.JSONArray());

        UpdateSite site = j.jenkins.getUpdateCenter().getSites().get(0);
        UpdateSite.Plugin plugin = site.new Plugin("test", pluginJson);

        // All allowed elements should be preserved, but list tags will be stripped
        assertThat(plugin.excerpt, is("Paragraph with <b>bold</b>, <i>italic</i>, <em>emphasis</em>, <strong>strong</strong>, <code>code</code>, and <a href=\"https://jenkins.io\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">links</a>Item 1Item 2"));
    }

    @Test
    void nullExcerptHandledGracefully(JenkinsRule j) {
        JSONObject pluginJson = new JSONObject();
        pluginJson.put("name", "test-plugin");
        pluginJson.put("version", "1.0");
        pluginJson.put("url", "http://example.com/test.hpi");
        // No excerpt field
        pluginJson.put("dependencies", new net.sf.json.JSONArray());

        UpdateSite site = j.jenkins.getUpdateCenter().getSites().get(0);
        UpdateSite.Plugin plugin = site.new Plugin("test", pluginJson);

        // Null excerpt should remain null, not throw exception
        assertNull(plugin.excerpt);
    }
}
