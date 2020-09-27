package hudson.util;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import jenkins.security.ConfidentialStoreRule;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SecretRewriterTest {

    @Rule
    public ConfidentialStoreRule confidentialStoreRule = new ConfidentialStoreRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Pattern FOO_PATTERN = Pattern.compile("<foo>[{][A-Za-z0-9+/]+={0,2}[}]</foo>");
    private static final Pattern MSG_PATTERN = Pattern.compile("<msg>[{][A-Za-z0-9+/]+={0,2}[}]</msg>");
    private static final Pattern FOO_PATTERN2 = Pattern.compile("(<foo>[{][A-Za-z0-9+/]+={0,2}[}]</foo>){2}");
    private static final Pattern ABC_FOO_PATTERN = Pattern.compile("<abc>\\s<foo>[{][A-Za-z0-9+/]+={0,2}[}]</foo>\\s</abc>");

    @Test
    public void singleFileRewrite() throws Exception {
        String o = encryptOld("foobar"); // old
        String n = encryptNew("foobar"); // new
        assertTrue(FOO_PATTERN.matcher(roundtrip("<foo>" + o + "</foo>")).matches());
        assertTrue(FOO_PATTERN2.matcher(roundtrip("<foo>" + o + "</foo><foo>" + o + "</foo>")).matches());
        assertEquals("<foo>" + n + "</foo>", roundtrip("<foo>" + n + "</foo>"));
        assertEquals("<foo>thisIsLegalBase64AndLongEnoughThatItCouldLookLikeSecret</foo>", roundtrip("  <foo>thisIsLegalBase64AndLongEnoughThatItCouldLookLikeSecret</foo>  "));
        // to be rewritten, it needs to be between a tag
        assertEquals("<foo>" + o, roundtrip("<foo>" + o));
        assertEquals(o + "</foo>", roundtrip(o + "</foo>"));
        assertTrue(ABC_FOO_PATTERN.matcher(roundtrip("<abc>\n<foo>" + o + "</foo>\n</abc>")).matches());
    }

    private String roundtrip(String before) throws Exception {
        SecretRewriter sr = new SecretRewriter(null);
        File f = File.createTempFile("test", "xml", tmp.getRoot());
        FileUtils.write(f, before, Charset.defaultCharset());
        sr.rewrite(f, null);
        //assert after.replaceAll(System.getProperty("line.separator"), "\n").trim()==f.text.replaceAll(System.getProperty("line.separator"), "\n").trim()
        return FileUtils.readFileToString(f, Charset.defaultCharset()).replaceAll(System.getProperty("line.separator"), "\n").trim();
    }

    private String encryptOld(String str) throws Exception {
        Cipher cipher = Secret.getCipher("AES");
        cipher.init(Cipher.ENCRYPT_MODE, HistoricalSecrets.getLegacyKey());
        return new String(Base64.getEncoder().encode(cipher.doFinal((str + HistoricalSecrets.MAGIC).getBytes(StandardCharsets.UTF_8))));
    }

    private String encryptNew(String str) {
        return Secret.fromString(str).getEncryptedValue();
    }

    /**
     * Directory rewrite and recursion detection
     */
    @Test
    public void recursionDetection() throws Exception {
        assumeFalse("Symlinks don't work on Windows very well", Functions.isWindows());
        SecretRewriter sw = new SecretRewriter();
        TaskListener st = StreamTaskListener.fromStdout();

        String o = encryptOld("Hello world");
        String n = encryptNew("Hello world");
        String payload = "<msg>" + o + "</msg>";

        // set up some directories with stuff
        File t = tmp.newFolder("t");
        List<String> dirs = Arrays.asList("a", "b", "c", "c/d", "c/d/e");
        for (String p : dirs) {
            File d = new File(t, p);
            d.mkdir();
            try {
                FileUtils.write(new File(d, "foo.xml"), payload, Charset.defaultCharset());
            } catch (IOException x) {
                assert false : x;
            }
        }

        // stuff outside
        File t2 = tmp.newFolder("t2");
        FileUtils.write(new File(t2, "foo.xml"), payload, Charset.defaultCharset());

        // some recursions as well as valid symlinks
        new FilePath(t).child("c/symlink").symlinkTo("..", st);
        new FilePath(t).child("b/symlink").symlinkTo(".", st);
        new FilePath(t).child("a/symlink").symlinkTo(t2.getAbsolutePath(), st);

        assertEquals(6, sw.rewriteRecursive(t, st));

        for (String p : dirs) {
            assertTrue(MSG_PATTERN.matcher(FileUtils.readFileToString(new File(t, p + "/foo.xml"), Charset.defaultCharset()).trim()).matches());
        }

        // t2 is only reachable by following a symlink. this should be covered, too
        assertTrue(MSG_PATTERN.matcher(FileUtils.readFileToString(new File(t2, "foo.xml"), Charset.defaultCharset()).trim()).matches());
    }

}
