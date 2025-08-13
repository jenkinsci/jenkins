package hudson.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import hudson.model.Items;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import jenkins.security.ClassFilterImpl;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@WithJenkins
class XStream2Security383Test {

    @TempDir
    private File f;

    @Mock
    private StaplerRequest2 req;

    @Mock
    private StaplerResponse2 rsp;

    private AutoCloseable mocks;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    @Issue("SECURITY-383")
    void testXmlLoad() throws Exception {
        File exploitFile = File.createTempFile("junit", null, f);
        try {
            // be extra sure there's no file already
            if (exploitFile.exists() && !exploitFile.delete()) {
                throw new IllegalStateException("file exists and cannot be deleted");
            }
            File tempJobDir = new File(j.jenkins.getRootDir(), "security383");

            String exploitXml = IOUtils.toString(
                    XStream2Security383Test.class.getResourceAsStream(
                            "/hudson/util/XStream2Security383Test/config.xml"), StandardCharsets.UTF_8);

            exploitXml = exploitXml.replace("@TOKEN@", exploitFile.getAbsolutePath());

            Files.createDirectory(tempJobDir.toPath());
            Files.writeString(tempJobDir.toPath().resolve("config.xml"), exploitXml, StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> Items.load(j.jenkins, tempJobDir));
            assertFalse(exploitFile.exists(), "no file should be created here");
        } finally {
            exploitFile.delete();
        }
    }

    @Test
    @Issue("SECURITY-383")
    void testPostJobXml() throws Exception {
        File exploitFile = File.createTempFile("junit", null, f);
        try {
            // be extra sure there's no file already
            if (exploitFile.exists() && !exploitFile.delete()) {
                throw new IllegalStateException("file exists and cannot be deleted");
            }
            File tempJobDir = new File(j.jenkins.getRootDir(), "security383");

            String exploitXml = IOUtils.toString(
                    XStream2Security383Test.class.getResourceAsStream(
                            "/hudson/util/XStream2Security383Test/config.xml"), StandardCharsets.UTF_8);

            exploitXml = exploitXml.replace("@TOKEN@", exploitFile.getAbsolutePath());

            when(req.getMethod()).thenReturn("POST");
            when(req.getInputStream()).thenReturn(new Stream(IOUtils.toInputStream(exploitXml, StandardCharsets.UTF_8)));
            when(req.getContentType()).thenReturn("application/xml");
            when(req.getParameter("name")).thenReturn("foo");

            assertThrows(IOException.class, () -> j.jenkins.doCreateItem(req, rsp));
            assertFalse(exploitFile.exists(), "no file should be created here");
        } finally {
            exploitFile.delete();
        }
    }

    private static class Stream extends ServletInputStream {
        private final InputStream inner;

        Stream(final InputStream inner) {
            this.inner = inner;
        }

        @Override
        public int read() throws IOException {
            return inner.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inner.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inner.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
