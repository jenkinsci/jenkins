package hudson.console;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Launcher;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.triggers.SCMTrigger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.fileupload2.core.MultipartInput;
import org.htmlunit.Page;
import org.htmlunit.TextPage;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
public class ConsoleAnnotatorTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Let the build complete, and see if stateless {@link ConsoleAnnotator} annotations happen as expected.
     */
    @Issue("JENKINS-6031")
    @Test
    void completedStatelessLogAnnotation() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println("---");
                listener.getLogger().println("ooo");
                listener.getLogger().println("ooo");
                return true;
            }
        });

        FreeStyleBuild b = r.buildAndAssertSuccess(p);

        // make sure we see the annotation
        HtmlPage rsp = r.createWebClient().getPage(b, "console");
        assertEquals(1, DomNodeUtil.selectNodes(rsp, "//B[@class='demo']").size());

        // make sure raw console output doesn't include the garbage
        TextPage raw = (TextPage) r.createWebClient().goTo(b.getUrl() + "consoleText", "text/plain");
        System.out.println(raw.getContent());
        String nl = System.lineSeparator();
        assertTrue(raw.getContent().contains(nl + "---" + nl + "ooo" + nl + "ooo" + nl));

        // there should be two 'ooo's
        String xml = rsp.asXml();
        assertEquals(3, xml.split("ooo").length, xml);
    }

    /**
     * Only annotates the first occurrence of "ooo".
     */
    @TestExtension("completedStatelessLogAnnotation")
    public static final class DemoAnnotatorFactory extends ConsoleAnnotatorFactory<FreeStyleBuild> {
        @Override
        public ConsoleAnnotator<FreeStyleBuild> newInstance(FreeStyleBuild context) {
            return new DemoAnnotator();
        }
    }

    public static class DemoAnnotator extends ConsoleAnnotator<FreeStyleBuild> {
        private static final String ANNOTATE_TEXT = "ooo" + System.lineSeparator();

        @Override
        public ConsoleAnnotator<FreeStyleBuild> annotate(FreeStyleBuild build, MarkupText text) {
            if (text.getText().equals(ANNOTATE_TEXT)) {
                text.addMarkup(0, 3, "<b class=demo>", "</b>");
                return null;
            }
            return this;
        }
    }

    @Issue("JENKINS-6034")
    @Test
    void consoleAnnotationFilterOut() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().print("abc\n");
                listener.getLogger().print(HyperlinkNote.encodeTo("http://infradna.com/", "def") + "\n");
                return true;
            }
        });

        FreeStyleBuild b = r.buildAndAssertSuccess(p);

        // make sure we see the annotation
        HtmlPage rsp = r.createWebClient().getPage(b, "console");
        assertEquals(1, DomNodeUtil.selectNodes(rsp, "//A[@href='http://infradna.com/']").size());

        // make sure raw console output doesn't include the garbage
        TextPage raw = (TextPage) r.createWebClient().goTo(b.getUrl() + "consoleText", "text/plain");
        assertThat(raw.getContent(), containsString("\nabc\ndef\n"));
    }





    class ProgressiveLogClient {
        JenkinsRule.WebClient wc;
        Run run;
        boolean streaming;

        String consoleAnnotator;
        String start;
        private Page p;

        ProgressiveLogClient(JenkinsRule.WebClient wc, Run r, boolean streaming) {
            this.wc = wc;
            this.run = r;
            this.streaming = streaming;
        }

        String next() throws IOException {
            WebRequest req = new WebRequest(new URL(r.getURL() + run.getUrl() + "/logText/progressiveHtml" + (start != null ? "?start=" + start : "")));
            req.setEncodingType(null);
            Map<String, String> headers = new HashMap<>();
            if (consoleAnnotator != null)
                headers.put("X-ConsoleAnnotator", consoleAnnotator);
            if (streaming) headers.put("Accept", "multipart/form-data");
            req.setAdditionalHeaders(headers);

            p = wc.getPage(req);
            String content;
            if (streaming) {
                var ct = p.getWebResponse().getResponseHeaderValue("Content-Type");
                assertTrue(ct.matches("^multipart/form-data;boundary=[a-f0-9-]{36};charset=utf-8$"));
                var boundary = ct.substring(29, 29 + 36).getBytes();
                var mp = new MultipartInput.Builder()
                        .setBoundary(boundary)
                        .setInputStream(p.getWebResponse().getContentAsStream())
                        .get();
                var text = new ByteArrayOutputStream();
                mp.skipPreamble();
                assertEquals("Content-Disposition: form-data;name=text\r\nContent-Type: text/html;charset=utf-8\r\n\r\n", mp.readHeaders());
                mp.readBodyData(text);

                var metaBAOS = new ByteArrayOutputStream();
                mp.readBoundary();
                assertEquals("Content-Disposition: form-data;name=meta\r\nContent-Type: application/json;charset=utf-8\r\n\r\n", mp.readHeaders());
                mp.readBodyData(metaBAOS);

                content = text.toString();
                var meta = JSONObject.fromObject(metaBAOS.toString());
                consoleAnnotator = meta.getString("consoleAnnotator");
                start = meta.getString("end");
            } else {
                content = p.getWebResponse().getContentAsString();
                consoleAnnotator = p.getWebResponse().getResponseHeaderValue("X-ConsoleAnnotator");
                start = p.getWebResponse().getResponseHeaderValue("X-Text-Size");
            }
            return content;
        }

    }

    /**
     * Tests the progressive output by making sure that the state of {@link ConsoleAnnotator}s are
     * maintained across different progressiveLog calls.
     */
    @Test
    void progressiveOutput() throws Exception {
        progressiveOutputWith(false, "\r\n");
    }

    @Test
    void progressiveOutputStreaming() throws Exception {
        progressiveOutputWith(true, System.lineSeparator());
    }

    void progressiveOutputWith(boolean streaming, String linefeed) throws Exception {
        final SequenceLock lock = new SequenceLock();
        JenkinsRule.WebClient wc = r.createWebClient();
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                lock.phase(0);
                // make sure the build is now properly started
                lock.phase(2);
                listener.getLogger().println("line1");
                lock.phase(4);
                listener.getLogger().println("line2");
                lock.phase(6);
                return true;
            }
        });
        Future<FreeStyleBuild> f = p.scheduleBuild2(0);


        lock.phase(1);
        FreeStyleBuild b = p.getBuildByNumber(1);
        ProgressiveLogClient plc = new ProgressiveLogClient(wc, b, streaming);
        // the page should contain some output indicating the build has started why and etc.
        var preamble = plc.next();
        assertEquals(false, preamble.isEmpty());

        lock.phase(3);
        assertEquals("<b tag=1>line1</b>" + linefeed, plc.next());

        // the new invocation should start from where the previous call left off
        lock.phase(5);
        assertEquals("<b tag=2>line2</b>" + linefeed, plc.next());

        lock.done();

        // should complete successfully
        r.assertBuildStatusSuccess(f);
    }

    @TestExtension({"progressiveOutput", "progressiveOutputStreaming"})
    public static final ConsoleAnnotatorFactory STATEFUL_ANNOTATOR = new ConsoleAnnotatorFactory() {
        @Override
        public ConsoleAnnotator newInstance(Object context) {
            return new StatefulAnnotator();
        }
    };

    public static class StatefulAnnotator extends ConsoleAnnotator<Object> {
        int n = 1;

        @Override
        public ConsoleAnnotator annotate(Object build, MarkupText text) {
            if (text.getText().startsWith("line"))
                text.addMarkup(0, 5, "<b tag=" + n++ + ">", "</b>");
            return this;
        }

        @Override
        public String toString() {
            return "StatefulAnnotator:" + n + " @" + System.identityHashCode(this);
        }
    }


    /**
     * Place {@link ConsoleNote}s and make sure it works.
     */
    @Test
    void consoleAnnotation() throws Exception {
        consoleAnnotationWith(false, "\r\n");
    }

    @Test
    void consoleAnnotationStreaming() throws Exception {
        consoleAnnotationWith(true, System.lineSeparator());
    }

    /**
     * Place {@link ConsoleNote}s and make sure it works.
     */
    void consoleAnnotationWith(boolean streaming, String lineEndings) throws Exception {
        final SequenceLock lock = new SequenceLock();
        JenkinsRule.WebClient wc = r.createWebClient();
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                lock.phase(0);
                // make sure the build is now properly started

                lock.phase(2);
                listener.getLogger().print("abc");
                listener.annotate(new DollarMark());
                listener.getLogger().println("def");

                lock.phase(4);
                listener.getLogger().print("123");
                listener.annotate(new DollarMark());
                listener.getLogger().print("456");
                listener.annotate(new DollarMark());
                listener.getLogger().println("789");

                lock.phase(6);
                return true;
            }
        });
        Future<FreeStyleBuild> f = p.scheduleBuild2(0);

        // discard the initial header portion
        lock.phase(1);
        FreeStyleBuild b = p.getBuildByNumber(1);
        ProgressiveLogClient plc = new ProgressiveLogClient(wc, b, streaming);
        plc.next();

        lock.phase(3);
        assertEquals("abc$$$def" + lineEndings, plc.next());

        lock.phase(5);
        assertEquals("123$$$456$$$789" + lineEndings, plc.next());

        lock.done();

        // should complete successfully
        r.assertBuildStatusSuccess(f);
    }

    /**
     * Places a triple dollar mark at the specified position.
     */
    public static final class DollarMark extends ConsoleNote<Object> {
        @Override
        public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
            text.addMarkup(charPos, "$$$");
            return null;
        }

        @TestExtension
        public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {}
    }


    /**
     * script.js defined in the annotator needs to be incorporated into the console page.
     */
    @Test
    void scriptInclusion() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b = r.buildAndAssertSuccess(p);

        HtmlPage html = r.createWebClient().getPage(b, "console");
        // verify that there's an element inserted by the script
        assertNotNull(html.getElementById("inserted-by-test1"));
        assertNotNull(html.getElementById("inserted-by-test2"));
        for (DomElement e : html.getElementsByTagName("script")) {
            String src = e.getAttribute("src");
            if (!src.isEmpty()) {
                assertThat(src, containsString(Jenkins.SESSION_HASH));
            }
        }
    }

    public static final class JustToIncludeScript extends ConsoleNote<Object> {
        @Override
        public ConsoleAnnotator annotate(Object build, MarkupText text, int charPos) {
            return null;
        }

        @TestExtension("scriptInclusion")
        public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {}
    }

    @TestExtension("scriptInclusion")
    public static final class JustToIncludeScriptAnnotator extends ConsoleAnnotatorFactory {
        @Override
        public ConsoleAnnotator newInstance(Object context) {
            return null;
        }
    }


    /**
     * Makes sure '<', '&', are escaped.
     */
    @Issue("JENKINS-5952")
    @Test
    void escape() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println("<b>&amp;</b>");
                return true;
            }
        });

        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        HtmlPage html = r.createWebClient().getPage(b, "console");
        String text = html.asNormalizedText();
        System.out.println(text);
        assertTrue(text.contains("<b>&amp;</b>"));
        assertTrue(JenkinsRule.getLog(b).contains("<b>&amp;</b>"));
    }


    /**
     * Makes sure that annotations in the polling output is handled correctly.
     */
    @Test
    void pollingOutput() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new PollingSCM());
        SCMTrigger t = new SCMTrigger("@daily");
        t.start(p, true);
        p.addTrigger(t);

        r.buildAndAssertSuccess(p);

        // poll now
        t.new Runner().run();

        HtmlPage log = r.createWebClient().getPage(p, "scmPollLog");
        String text = log.asNormalizedText();
        assertTrue(text.contains("$$$hello from polling"), text);
    }

    public static class PollingSCM extends SingleFileSCM {
        public PollingSCM() throws UnsupportedEncodingException {
            super("abc", "def");
        }

        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException {
            listener.annotate(new DollarMark());
            listener.getLogger().println("hello from polling");
            return new PollingResult(Change.NONE);
        }

        @TestExtension
        public static final class DescriptorImpl extends SCMDescriptor<PollingSCM> {
            public DescriptorImpl() {
                super(PollingSCM.class, RepositoryBrowser.class);
            }
        }
    }
}
