package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import net.sf.json.JSONObject;
import org.junit.Test;

public class UpdateCenterTest {

    @Test
    public void toUpdateCenterCheckUrl_http_noQuery() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "http://updates.jenkins-ci.org/update-center.json").toExternalForm(),
                is("http://updates.jenkins-ci.org/update-center.json?uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_https_noQuery() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "https://updates.jenkins-ci.org/update-center.json").toExternalForm(),
                is("https://updates.jenkins-ci.org/update-center.json?uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_http_query() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "http://updates.jenkins-ci.org/update-center.json?version=2.7").toExternalForm(),
                is("http://updates.jenkins-ci.org/update-center.json?version=2.7&uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_https_query() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "https://updates.jenkins-ci.org/update-center.json?version=2.7").toExternalForm(),
                is("https://updates.jenkins-ci.org/update-center.json?version=2.7&uctest"));
    }

    @Test
    public void toUpdateCenterCheckUrl_file() throws Exception {
        assertThat(UpdateCenter.UpdateCenterConfiguration.toUpdateCenterCheckUrl(
                "file://./foo.jar!update-center.json").toExternalForm(),
                is("file://./foo.jar!update-center.json"));
    }

    @Test
    public void noChecksums() {
        final IOException ex = assertThrows(IOException.class,
                () -> UpdateCenter.verifyChecksums(new MockDownloadJob(null, null, null),
                        buildEntryWithExpectedChecksums(null, null, null), new File("example")));
        assertEquals("Unable to confirm integrity of downloaded file, refusing installation", ex.getMessage());
    }

    @Test
    public void sha1Match() throws Exception {
        UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, null, null),
                buildEntryWithExpectedChecksums(EMPTY_SHA1, null, null), new File("example"));
    }

    @Test
    public void sha1Mismatch() {
        final IOException ex = assertThrows(IOException.class, () -> UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1.replace('k', 'f'), null, null),
                buildEntryWithExpectedChecksums(EMPTY_SHA1, null, null), new File("example")));
        assertTrue(ex.getMessage().contains("does not match expected SHA-1, expected '2jmj7l5rSw0yVb/vlWAYkK/YBwk=', actual '2jmj7l5rSw0yVb/vlWAYfK/YBwf='"));
    }

    @Test
    public void sha512ProvidedOnly() throws IOException {
        UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, EMPTY_SHA256, EMPTY_SHA512),
                buildEntryWithExpectedChecksums(null, null, EMPTY_SHA512), new File("example"));
    }

    @Test
    public void sha512and256IgnoreCase() throws IOException {
        UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, EMPTY_SHA256.toUpperCase(Locale.US), EMPTY_SHA512.toUpperCase(Locale.US)),
                buildEntryWithExpectedChecksums(null, EMPTY_SHA256, EMPTY_SHA512), new File("example"));
    }

    @Test
    public void sha1DoesNotIgnoreCase() {
        final Exception ex = assertThrows(Exception.class, () -> UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, EMPTY_SHA256, EMPTY_SHA512),
                buildEntryWithExpectedChecksums(EMPTY_SHA1.toUpperCase(Locale.US), null, null), new File("example")));
        assertTrue(ex.getMessage().contains("does not match expected SHA-1, expected '2JMJ7L5RSW0YVB/VLWAYKK/YBWK=', actual '2jmj7l5rSw0yVb/vlWAYkK/YBwk='"));
    }

    @Test
    public void noOverlapForComputedAndProvidedChecksums() {
        final Exception ex = assertThrows(Exception.class, () -> UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, EMPTY_SHA256, null),
                buildEntryWithExpectedChecksums(null, null, EMPTY_SHA512), new File("example")));
        assertEquals("Unable to confirm integrity of downloaded file, refusing installation", ex.getMessage());
    }

    @Test
    public void noOverlapForComputedAndProvidedChecksumsForSpecIncompliantJVM() {
        final Exception ex = assertThrows(Exception.class, () -> UpdateCenter.verifyChecksums(
                new MockDownloadJob(EMPTY_SHA1, null, null),
                buildEntryWithExpectedChecksums(null, EMPTY_SHA256, EMPTY_SHA512), new File("example")));
        assertEquals("Unable to confirm integrity of downloaded file, refusing installation", ex.getMessage());
    }


    private static final String EMPTY_SHA1 = "2jmj7l5rSw0yVb/vlWAYkK/YBwk=";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String EMPTY_SHA512 = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";

    private static UpdateSite.Entry buildEntryWithExpectedChecksums(String expectedSHA1, String expectedSHA256, String expectedSHA512) {
        JSONObject o = new JSONObject();
        o.put("name", "unnamed");
        o.put("version", "unspecified");
        o.put("url", "https://example.invalid");
        if (expectedSHA1 != null) {
            o.put("sha1", expectedSHA1);
        }
        if (expectedSHA256 != null) {
            o.put("sha256", expectedSHA256);
        }
        if (expectedSHA512 != null) {
            o.put("sha512", expectedSHA512);
        }
        return new MockEntry(o);
    }

    private static class MockEntry extends UpdateSite.Entry {

        MockEntry(JSONObject o) {
            // needs name, version, url, optionally sha1, sha256, sha512
            super("default", o);
        }
    }

    private static class MockDownloadJob implements UpdateCenter.WithComputedChecksums {

        private final String computedSHA1;
        private final String computedSHA256;
        private final String computedSHA512;

        MockDownloadJob(String computedSHA1, String computedSHA256, String computedSHA512) {
            this.computedSHA1 = computedSHA1;
            this.computedSHA256 = computedSHA256;
            this.computedSHA512 = computedSHA512;
        }

        @Override
        public String getComputedSHA1() {
            return this.computedSHA1;
        }

        @Override
        public String getComputedSHA256() {
            return computedSHA256;
        }

        @Override
        public String getComputedSHA512() {
            return computedSHA512;
        }
    }
}
