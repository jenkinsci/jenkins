package jenkins.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import hudson.util.Secret;
import hudson.util.SecretHelper;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import org.apache.commons.io.FileUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RekeySecretAdminMonitorTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    RekeySecretAdminMonitor monitor;

    final String plain_regex_match = ".*\\{[A-Za-z0-9+/]+={0,2}}.*";

    @Before
    public void setUp() {
        monitor = ExtensionList.lookupSingleton(RekeySecretAdminMonitor.class);
    }

    @JenkinsRecipe(RekeySecretAdminMonitorTest.WithTestSecret.RuleRunnerImpl.class)
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface WithTestSecret {
        class RuleRunnerImpl extends JenkinsRecipe.Runner<RekeySecretAdminMonitorTest.WithTestSecret> {
            @Override
            public void setup(JenkinsRule jenkinsRule, WithTestSecret recipe) {
                SecretHelper.set(TEST_KEY);
            }

            @Override
            public void tearDown(JenkinsRule jenkinsRule, WithTestSecret recipe) {
                SecretHelper.set(null);
            }
        }
    }

    @JenkinsRecipe(RekeySecretAdminMonitorTest.WithScanOnBoot.RuleRunnerImpl.class)
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface WithScanOnBoot {
        class RuleRunnerImpl extends JenkinsRecipe.Runner<RekeySecretAdminMonitorTest.WithScanOnBoot> {
            @Override
            public void decorateHome(JenkinsRule jenkinsRule, File home) throws Exception {
                // schedule a scan on boot
                File f = new File(home, RekeySecretAdminMonitor.class.getName() + "/scanOnBoot");
                f.getParentFile().mkdirs();
                new FilePath(f).touch(0);

                // and stage some data
                putSomeOldData(home);
            }
        }
    }

    private static void putSomeOldData(File dir) throws Exception {
        File xml = new File(dir, "foo.xml");
        FileUtils.writeStringToFile(xml, "<foo>" + encryptOld(TEST_KEY) + "</foo>", StandardCharsets.UTF_8);
    }

    private void verifyRewrite(File dir) throws Exception {
        File xml = new File(dir, "foo.xml");
        Pattern pattern = Pattern.compile("<foo>" + plain_regex_match + "</foo>");
        MatcherAssert.assertThat(FileUtils.readFileToString(xml, StandardCharsets.UTF_8).trim(), Matchers.matchesRegex(pattern));
    }

    @WithTestSecret
    @Test
    public void testBasicWorkflow() throws Exception {
        putSomeOldData(j.jenkins.getRootDir());
        monitor.setNeeded();

        JenkinsRule.WebClient wc = j.createWebClient();

        // one should see the warning. try scheduling it
        assertFalse(monitor.isScanOnBoot());
        HtmlForm form = getRekeyForm(wc);
        j.submit(form, "schedule");
        assertTrue(monitor.isScanOnBoot());
        form = getRekeyForm(wc);
        assertTrue(getButton(form, 1).isDisabled());

        // run it now
        assertFalse(monitor.getLogFile().exists());
        j.submit(form, "background");
        assertTrue(monitor.getLogFile().exists());

        // should be no warning/error now
        HtmlPage manage = wc.goTo("manage");
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='error']").size());
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='warning']").size());

        // and the data should be rewritten
        verifyRewrite(j.jenkins.getRootDir());
        assertTrue(monitor.isDone());

        // dismiss and the message will be gone
        assertTrue(monitor.isEnabled());
        form = getRekeyForm(wc);
        j.submit(form, "dismiss");
        assertFalse(monitor.isEnabled());
        assertThrows(ElementNotFoundException.class, () -> getRekeyForm(wc));
    }

    private HtmlForm getRekeyForm(JenkinsRule.WebClient wc) throws IOException, SAXException {
        return wc.goTo("manage").getFormByName("rekey");
    }

    private HtmlButton getButton(HtmlForm form, int index) {
        // due to the removal of method HtmlElement.getHtmlElementsByTagName
        Stream<HtmlButton> buttonStream = form.getElementsByTagName("button").stream()
                .filter(HtmlButton.class::isInstance)
                .map(HtmlButton.class::cast);

        if (index > 0) {
            buttonStream = buttonStream.skip(index);
        }

        return buttonStream
                .findFirst()
                .orElse(null);
    }

    @WithTestSecret
    @WithScanOnBoot
    @Test
    public void testScanOnBoot() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        // scan on boot should have run the scan
        assertTrue(monitor.getLogFile().exists());
        assertFalse("scan on boot should have turned this off", monitor.isScanOnBoot());

        // and data should be migrated
        verifyRewrite(j.jenkins.getRootDir());

        // should be no warning/error now
        HtmlPage manage = wc.goTo("manage");
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='error']").size());
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='warning']").size());
    }

    private static String encryptOld(String str) throws Exception {
        Cipher cipher = Secret.getCipher("AES");
        cipher.init(Cipher.ENCRYPT_MODE, Util.toAes128Key(TEST_KEY));
        return Base64.getEncoder().encodeToString(cipher.doFinal((str + "::::MAGIC::::").getBytes(StandardCharsets.UTF_8)));
    }

    private String encryptNew(String str) {
        return Secret.fromString(str).getEncryptedValue();
    }

    private static final String TEST_KEY = "superDuperSecretWasNotSoSecretAfterAll";
}
