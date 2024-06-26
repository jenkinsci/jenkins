package hudson.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class XStream2Security383Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder f = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClassFilterImpl.class, Level.FINE);

    @Mock
    private StaplerRequest2 req;

    @Mock
    private StaplerResponse2 rsp;

    private AutoCloseable mocks;

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    @Issue("SECURITY-383")
    public void testXmlLoad() throws Exception {
        File exploitFile = f.newFile();
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
            assertFalse("no file should be created here", exploitFile.exists());
        } finally {
            exploitFile.delete();
        }
    }

    @Test
    @Issue("SECURITY-383")
    public void testPostJobXml() throws Exception {
        File exploitFile = f.newFile();
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
            assertFalse("no file should be created here", exploitFile.exists());
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
