package hudson.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import jenkins.security.ConfidentialStoreRule;
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
        SecretRewriter sr = new SecretRewriter();
        Path path = Files.createTempFile(tmp.getRoot().toPath(), "test", "xml");
        Files.writeString(path, before, Charset.defaultCharset());
        sr.rewrite(path.toFile());
        //assert after.replaceAll(System.getProperty("line.separator"), "\n").trim()==f.text.replaceAll(System.getProperty("line.separator"), "\n").trim()
        return Files.readString(path, Charset.defaultCharset()).replaceAll(System.getProperty("line.separator"), "\n").trim();
    }

    @SuppressWarnings("deprecation")
    private String encryptOld(String str) throws Exception {
        Cipher cipher = Secret.getCipher("AES");
        cipher.init(Cipher.ENCRYPT_MODE, HistoricalSecrets.getLegacyKey());
        return Base64.getEncoder().encodeToString(cipher.doFinal((str + HistoricalSecrets.MAGIC).getBytes(StandardCharsets.UTF_8)));
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
        List<String> dirs = List.of("a", "b", "c", "c/d", "c/d/e");
        for (String p : dirs) {
            File d = new File(t, p);
            d.mkdir();
            Files.writeString(d.toPath().resolve("foo.xml"), payload, Charset.defaultCharset());
        }

        // stuff outside
        File t2 = tmp.newFolder("t2");
        Files.writeString(t2.toPath().resolve("foo.xml"), payload, Charset.defaultCharset());

        // some recursions as well as valid symlinks
        new FilePath(t).child("c/symlink").symlinkTo("..", st);
        new FilePath(t).child("b/symlink").symlinkTo(".", st);
        new FilePath(t).child("a/symlink").symlinkTo(t2.getAbsolutePath(), st);

        assertEquals(6, sw.rewriteRecursive(t, st));

        for (String p : dirs) {
            assertTrue(MSG_PATTERN.matcher(Files.readString(new File(t, p + "/foo.xml").toPath(), Charset.defaultCharset()).trim()).matches());
        }

        // t2 is only reachable by following a symlink. this should be covered, too
        assertTrue(MSG_PATTERN.matcher(Files.readString(new File(t2, "foo.xml").toPath(), Charset.defaultCharset()).trim()).matches());
    }

}
