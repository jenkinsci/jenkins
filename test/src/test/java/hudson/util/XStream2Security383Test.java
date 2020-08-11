package hudson.util;

import hudson.model.Items;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import jenkins.security.ClassFilterImpl;

import static org.junit.Assert.assertFalse;
import org.jvnet.hudson.test.LoggerRule;
import static org.mockito.Mockito.when;

public class XStream2Security383Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder f = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClassFilterImpl.class, Level.FINE);

    @Mock
    private StaplerRequest req;

    @Mock
    private StaplerResponse rsp;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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

            FileUtils.write(new File(tempJobDir, "config.xml"), exploitXml, StandardCharsets.UTF_8);

            try {
                Items.load(j.jenkins, tempJobDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
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

            try {
                j.jenkins.doCreateItem(req, rsp);
            } catch (Exception e) {
                e.printStackTrace();
            }

            assertFalse("no file should be created here", exploitFile.exists());
        } finally {
            exploitFile.delete();
        }
    }

    private static class Stream extends ServletInputStream {
        private final InputStream inner;

        public Stream(final InputStream inner) {
            this.inner = inner;
        }

        @Override
        public int read() throws IOException {
            return inner.read();
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
