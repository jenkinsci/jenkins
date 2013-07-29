package hudson.console;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
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
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsoleAnnotatorTest extends HudsonTestCase {
    /**
     * Let the build complete, and see if stateless {@link ConsoleAnnotator} annotations happen as expected.
     */
    @Bug(6031)
    public void testCompletedStatelessLogAnnotation() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("---");
                listener.getLogger().println("ooo");
                listener.getLogger().println("ooo");
                return true;
            }
        });

        FreeStyleBuild b = buildAndAssertSuccess(p);

        // make sure we see the annotation
        HtmlPage rsp = createWebClient().getPage(b, "console");
        assertEquals(1,rsp.selectNodes("//B[@class='demo']").size());

        // make sure raw console output doesn't include the garbage
        TextPage raw = (TextPage)createWebClient().goTo(b.getUrl()+"consoleText","text/plain");
        System.out.println(raw.getContent());
        String nl = System.getProperty("line.separator");
        assertTrue(raw.getContent().contains(nl+"---"+nl+"ooo"+nl+"ooo"+nl));

        // there should be two 'ooo's
        String xml = rsp.asXml();
        assertEquals(xml, 3, xml.split("ooo").length);
    }

    /**
     * Only annotates the first occurrence of "ooo".
     */
    @TestExtension("testCompletedStatelessLogAnnotation")
    public static final ConsoleAnnotatorFactory DEMO_ANNOTATOR = new ConsoleAnnotatorFactory() {
        public ConsoleAnnotator newInstance(Object context) {
            return new DemoAnnotator();
        }
    };

    public static class DemoAnnotator extends ConsoleAnnotator<Object> {
        private static final String ANNOTATE_TEXT = "ooo" + System.getProperty("line.separator");
        @Override
        public ConsoleAnnotator annotate(Object build, MarkupText text) {
            if (text.getText().equals(ANNOTATE_TEXT)) {
                text.addMarkup(0,3,"<b class=demo>","</b>");
                return null;
            }
            return this;
        }
    }

    @Bug(6034)
    public void testConsoleAnnotationFilterOut() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().print("abc\n");
                listener.getLogger().print(HyperlinkNote.encodeTo("http://infradna.com/","def")+"\n");
                return true;
            }
        });

        FreeStyleBuild b = buildAndAssertSuccess(p);

        // make sure we see the annotation
        HtmlPage rsp = createWebClient().getPage(b, "console");
        assertEquals(1,rsp.selectNodes("//A[@href='http://infradna.com/']").size());

        // make sure raw console output doesn't include the garbage
        TextPage raw = (TextPage)createWebClient().goTo(b.getUrl()+"consoleText","text/plain");
        System.out.println(raw.getContent());
        assertTrue(raw.getContent().contains("\nabc\ndef\n"));
    }





    class ProgressiveLogClient {
        WebClient wc;
        Run run;

        String consoleAnnotator;
        String start;
        private Page p;

        ProgressiveLogClient(WebClient wc, Run r) {
            this.wc = wc;
            this.run = r;
        }

        String next() throws IOException {
            WebRequestSettings req = new WebRequestSettings(new URL(getURL() + run.getUrl() + "/logText/progressiveHtml"+(start!=null?"?start="+start:"")));
            Map headers = new HashMap();
            if (consoleAnnotator!=null)
                headers.put("X-ConsoleAnnotator",consoleAnnotator);
            req.setAdditionalHeaders(headers);

            p = wc.getPage(req);
            consoleAnnotator = p.getWebResponse().getResponseHeaderValue("X-ConsoleAnnotator");
            start = p.getWebResponse().getResponseHeaderValue("X-Text-Size");
            return p.getWebResponse().getContentAsString();
        }

    }

    /**
     * Tests the progressive output by making sure that the state of {@link ConsoleAnnotator}s are
     * maintained across different progressiveLog calls.
     */
    public void testProgressiveOutput() throws Exception {
        final SequenceLock lock = new SequenceLock();
        WebClient wc = createWebClient();
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
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
        ProgressiveLogClient plc = new ProgressiveLogClient(wc,b);
        // the page should contain some output indicating the build has started why and etc.
        plc.next();

        lock.phase(3);
        assertEquals("<b tag=1>line1</b>\r\n",plc.next());

        // the new invocation should start from where the previous call left off
        lock.phase(5);
        assertEquals("<b tag=2>line2</b>\r\n",plc.next());

        lock.done();

        // should complete successfully
        assertBuildStatusSuccess(f);
    }

    @TestExtension("testProgressiveOutput")
    public static final ConsoleAnnotatorFactory STATEFUL_ANNOTATOR = new ConsoleAnnotatorFactory() {
        public ConsoleAnnotator newInstance(Object context) {
            return new StatefulAnnotator();
        }
    };

    public static class StatefulAnnotator extends ConsoleAnnotator<Object> {
        int n=1;

        public ConsoleAnnotator annotate(Object build, MarkupText text) {
            if (text.getText().startsWith("line"))
                text.addMarkup(0,5,"<b tag="+(n++)+">","</b>");
            return this;
        }
    }


    /**
     * Place {@link ConsoleNote}s and make sure it works.
     */
    public void testConsoleAnnotation() throws Exception {
        final SequenceLock lock = new SequenceLock();
        WebClient wc = createWebClient();
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
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
        ProgressiveLogClient plc = new ProgressiveLogClient(wc,b);
        plc.next();

        lock.phase(3);
        assertEquals("abc$$$def\r\n",plc.next());

        lock.phase(5);
        assertEquals("123$$$456$$$789\r\n",plc.next());

        lock.done();

        // should complete successfully
        assertBuildStatusSuccess(f);
    }

    /**
     * Places a triple dollar mark at the specified position.
     */
    public static final class DollarMark extends ConsoleNote<Object> {
        public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
            text.addMarkup(charPos,"$$$");
            return null;
        }

        @TestExtension
        public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
            public String getDisplayName() {
                return "Dollar mark";
            }
        }
    }


    /**
     * script.js defined in the annotator needs to be incorporated into the console page.
     */
    public void testScriptInclusion() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b = buildAndAssertSuccess(p);

        HtmlPage html = createWebClient().getPage(b, "console");
        // verify that there's an element inserted by the script
        assertNotNull(html.getElementById("inserted-by-test1"));
        assertNotNull(html.getElementById("inserted-by-test2"));
    }

    public static final class JustToIncludeScript extends ConsoleNote<Object> {
        public ConsoleAnnotator annotate(Object build, MarkupText text, int charPos) {
            return null;
        }

        @TestExtension("testScriptInclusion")
        public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
            public String getDisplayName() {
                return "just to include a script";
            }
        }
    }

    @TestExtension("testScriptInclusion")
    public static final class JustToIncludeScriptAnnotator extends ConsoleAnnotatorFactory {
        public ConsoleAnnotator newInstance(Object context) {
            return null;
        }
    }


    /**
     * Makes sure '<', '&', are escaped.
     */
    @Bug(5952)
    public void testEscape() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("<b>&amp;</b>");
                return true;
            }
        });

        FreeStyleBuild b = buildAndAssertSuccess(p);
        HtmlPage html = createWebClient().getPage(b, "console");
        String text = html.asText();
        System.out.println(text);
        assertTrue(text.contains("<b>&amp;</b>"));
        assertTrue(b.getLog().contains("<b>&amp;</b>"));
    }


    /**
     * Makes sure that annotations in the polling output is handled correctly.
     */
    public void testPollingOutput() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new PollingSCM());
        SCMTrigger t = new SCMTrigger("@daily");
        t.start(p,true);
        p.addTrigger(t);

        buildAndAssertSuccess(p);

        // poll now
        t.new Runner().run();

        HtmlPage log = createWebClient().getPage(p, "scmPollLog");
        String text = log.asText();
        assertTrue(text, text.contains("$$$hello from polling"));
    }

    public static class PollingSCM extends SingleFileSCM {
        public PollingSCM() throws UnsupportedEncodingException {
            super("abc", "def");
        }

        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
            listener.annotate(new DollarMark());
            listener.getLogger().println("hello from polling");
            return new PollingResult(Change.NONE);
        }

        @TestExtension
        public static final class DescriptorImpl extends SCMDescriptor<PollingSCM> {
            public DescriptorImpl() {
                super(PollingSCM.class, RepositoryBrowser.class);
            }

            @Override
            public String getDisplayName() {
                return "Test SCM";
            }
        }
    }
}
