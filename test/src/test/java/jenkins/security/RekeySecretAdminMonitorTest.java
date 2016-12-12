package jenkins.security;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.trilead.ssh2.crypto.Base64;
import hudson.FilePath;
import hudson.Util;
import hudson.util.Secret;
import hudson.util.SecretHelper;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.Recipe.Runner;
import org.xml.sax.SAXException;

import javax.crypto.Cipher;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;

/**
 * @author Kohsuke Kawaguchi
 */
public class RekeySecretAdminMonitorTest extends HudsonTestCase {
    @Inject
    RekeySecretAdminMonitor monitor;

    @Override
    protected void setUp() throws Exception {
        SecretHelper.set(TEST_KEY);
        super.setUp();
        monitor.setNeeded();
    }

    @Override
    protected void tearDown() throws Exception {
        SecretHelper.set(null);
        super.tearDown();
    }

    @Override
    protected void recipe() throws Exception {
        super.recipe();
        recipes.add(new Runner() {
            @Override
            public void setup(HudsonTestCase testCase, Annotation recipe) throws Exception {
            }

            @Override
            public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
                if (getName().endsWith("testScanOnBoot")) {
                    // schedule a scan on boot
                    File f = new File(home, RekeySecretAdminMonitor.class.getName() + "/scanOnBoot");
                    f.getParentFile().mkdirs();
                    new FilePath(f).touch(0);

                    // and stage some data
                    putSomeOldData(home);
                }
            }

            @Override
            public void tearDown(HudsonTestCase testCase, Annotation recipe) throws Exception {
            }
        });
    }


    private void putSomeOldData(File dir) throws Exception {
        File xml = new File(dir, "foo.xml");
        FileUtils.writeStringToFile(xml,"<foo>" + encryptOld(TEST_KEY) + "</foo>");
    }

    private void verifyRewrite(File dir) throws Exception {
        File xml = new File(dir, "foo.xml");
        assertEquals("<foo>" + encryptNew(TEST_KEY) + "</foo>".trim(),
                FileUtils.readFileToString(xml).trim());
    }

    // TODO sometimes fails: "Invalid request submission: {json=[Ljava.lang.String;@2c46358e, .crumb=[Ljava.lang.String;@35661457}"
    public void _testBasicWorkflow() throws Exception {
        putSomeOldData(jenkins.getRootDir());

        WebClient wc = createWebClient();

        // one should see the warning. try scheduling it
        assertTrue(!monitor.isScanOnBoot());
        HtmlForm form = getRekeyForm(wc);
        submit(form, "schedule");
        assertTrue(monitor.isScanOnBoot());
        form = getRekeyForm(wc);
        assertTrue(getButton(form, 1).isDisabled());

        // run it now
        assertTrue(!monitor.getLogFile().exists());
        submit(form, "background");
        assertTrue(monitor.getLogFile().exists());

        // should be no warning/error now
        HtmlPage manage = wc.goTo("manage");
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='error']").size());
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='warning']").size());

        // and the data should be rewritten
        verifyRewrite(jenkins.getRootDir());
        assertTrue(monitor.isDone());

        // dismiss and the message will be gone
        assertTrue(monitor.isEnabled());
        form = getRekeyForm(wc);
        submit(form, "dismiss");
        assertFalse(monitor.isEnabled());
        try {
            getRekeyForm(wc);
            fail();
        } catch (ElementNotFoundException e) {
            // expected
        }
    }

    private HtmlForm getRekeyForm(WebClient wc) throws IOException, SAXException {
        return wc.goTo("manage").getFormByName("rekey");
    }

    private HtmlButton getButton(HtmlForm form, int index) {
        return form.<HtmlButton>getHtmlElementsByTagName("button").get(index);
    }

    public void testScanOnBoot() throws Exception {
        WebClient wc = createWebClient();

        // scan on boot should have run the scan
        assertTrue(monitor.getLogFile().exists());
        assertTrue("scan on boot should have turned this off",!monitor.isScanOnBoot());

        // and data should be migrated
        verifyRewrite(jenkins.getRootDir());

        // should be no warning/error now
        HtmlPage manage = wc.goTo("/manage");
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='error']").size());
        assertEquals(0, DomNodeUtil.selectNodes(manage, "//*[class='warning']").size());
    }

    private String encryptOld(String str) throws Exception {
        Cipher cipher = Secret.getCipher("AES");
        cipher.init(Cipher.ENCRYPT_MODE, Util.toAes128Key(TEST_KEY));
        return new String(Base64.encode(cipher.doFinal((str + "::::MAGIC::::").getBytes("UTF-8"))));
    }

    private String encryptNew(String str) {
        return Secret.fromString(str).getEncryptedValue();
    }

    private static final String TEST_KEY = "superDuperSecretWasNotSoSecretAfterAll";
}
