package hudson.util

import com.trilead.ssh2.crypto.Base64
import hudson.FilePath
import jenkins.security.ConfidentialStoreRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import javax.crypto.Cipher

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class SecretRewriterTest {
    @Rule
    public MockSecretRule mockSecretRule = new MockSecretRule()

    @Rule
    public ConfidentialStoreRule confidentialStoreRule = new ConfidentialStoreRule();

    @Rule public TemporaryFolder tmp = new TemporaryFolder()

    def FOO_PATTERN = /<foo>\{[A-Za-z0-9+\/]+={0,2}}<\/foo>/
    def MSG_PATTERN = /<msg>\{[A-Za-z0-9+\/]+={0,2}}<\/msg>/
    def FOO_PATTERN2 = /(<foo>\{[A-Za-z0-9+\/]+={0,2}}<\/foo>){2}/
    def ABC_FOO_PATTERN = /<abc>\s<foo>\{[A-Za-z0-9+\/]+={0,2}}<\/foo>\s<\/abc>/

    @Test
    void singleFileRewrite() {
        def o = encryptOld('foobar') // old
        def n = encryptNew('foobar') // new
        roundtrip "<foo>${o}</foo>",
                {assert it ==~ FOO_PATTERN}


        roundtrip "<foo>${o}</foo><foo>${o}</foo>",
                {assert it ==~ FOO_PATTERN2}

        roundtrip "<foo>${n}</foo>",
                {assert it == "<foo>${n}</foo>"}

        roundtrip "  <foo>thisIsLegalBase64AndLongEnoughThatItCouldLookLikeSecret</foo>  ",
                {assert it == "<foo>thisIsLegalBase64AndLongEnoughThatItCouldLookLikeSecret</foo>"}

        // to be rewritten, it needs to be between a tag
        roundtrip "<foo>$o", {assert it == "<foo>$o"}
        roundtrip "$o</foo>", {assert it == "$o</foo>"}

        //
        roundtrip "<abc>\n<foo>$o</foo>\n</abc>", {assert it ==~ ABC_FOO_PATTERN}
    }

    void roundtrip(String before, Closure check) {
        def sr = new SecretRewriter(null);
        def f = File.createTempFile("test", "xml", tmp.root)
        f.text = before
        sr.rewrite(f,null)
        check(f.text.replaceAll(System.getProperty("line.separator"), "\n").trim())
        //assert after.replaceAll(System.getProperty("line.separator"), "\n").trim()==f.text.replaceAll(System.getProperty("line.separator"), "\n").trim()
    }

    String encryptOld(str) {
        def cipher = Secret.getCipher("AES");
        cipher.init(Cipher.ENCRYPT_MODE, HistoricalSecrets.legacyKey);
        return new String(Base64.encode(cipher.doFinal((str + HistoricalSecrets.MAGIC).getBytes("UTF-8"))))
    }

    String encryptNew(str) {
        return Secret.fromString(str).encryptedValue
    }

    /**
     * Directory rewrite and recursion detection
     */
    @Test
    void recursionDetection() {
        def sw = new SecretRewriter();
        def st = StreamTaskListener.fromStdout()

        def o = encryptOld("Hello world")
        def n = encryptNew("Hello world")
        def payload = "<msg>$o</msg>"
        def answer = "<msg>$n</msg>"

        // set up some directories with stuff
        def t = tmp.newFolder("t")
        def dirs = ["a", "b", "c", "c/d", "c/d/e"]
        dirs.each { p ->
            def d = new File(t, p)
            d.mkdir()
            new File(d,"foo.xml").text = payload
        }

        // stuff outside
        def t2 = tmp.newFolder("t2")
        new File(t2,"foo.xml").text = payload

        // some recursions as well as valid symlinks
        new FilePath(t).child("c/symlink").symlinkTo("..",st)
        new FilePath(t).child("b/symlink").symlinkTo(".",st)
        new FilePath(t).child("a/symlink").symlinkTo(t2.absolutePath,st)

        assert 6==sw.rewriteRecursive(t, st)

        dirs.each { p->
            assert new File(t,"$p/foo.xml").text.trim() ==~ MSG_PATTERN
        }

        // t2 is only reachable by following a symlink. this should be covered, too
        assert new File(t2,"foo.xml").text.trim() ==~ MSG_PATTERN
    }

}
