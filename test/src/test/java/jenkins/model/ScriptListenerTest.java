package jenkins.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import groovy.lang.Binding;
import hudson.ExtensionList;
import hudson.cli.GroovyCommand;
import hudson.cli.GroovyshCommand;
import hudson.model.User;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Locale;
import javax.servlet.RequestDispatcher;
import jenkins.util.ScriptListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ScriptListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private final String testMessage = "light of the world";
    private final String script = String.format("System.out.println('%s')", testMessage);
    private PrintStream ps;
    private ByteArrayOutputStream altStdout;
    private String expectedOutFormat = "Script: '%s' from '%s' by '%s'";
    private DummyScriptUsageListener listener = new DummyScriptUsageListener();

    @Before
    public void setUp() {
        altStdout = new ByteArrayOutputStream();
        ps = new PrintStream(altStdout);
        ExtensionList.lookup(ScriptListener.class).add(listener);
    }

    @Test
    public void consoleUsageIsLogged() throws Exception {
        RequestDispatcher view = mock(RequestDispatcher.class);
        StaplerRequest req = mock(StaplerRequest.class);
        StaplerResponse rsp = mock(StaplerResponse.class);

        when(req.getMethod()).thenReturn("POST");
        when(req.getParameter("script")).thenReturn(script);
        when(req.getView(j.jenkins, "_scriptText.jelly")).thenReturn(view);
        j.jenkins.doScriptText(req, rsp);

        assertEquals(String.format(expectedOutFormat, script, "Script Console Controller", "SYSTEM"), altStdout.toString().trim());
    }

    @Test
    public void groovyCliUsageIsLogged() throws Exception {
        GroovyCommand cmd = new GroovyCommand();
        cmd.script = "=";
        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        cmd.main(new ArrayList<>(), Locale.ENGLISH, scriptStream, System.out, System.err);
        assertEquals(String.format(expectedOutFormat, script, "CLI/GroovyCommand", "null"), altStdout.toString().trim());
    }

    @Test
    public void groovyShCliUsageIsLogged() throws Exception {
        GroovyshCommand cmd = new GroovyshCommand();
        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());

        cmd.main(new ArrayList<>(), Locale.ENGLISH, scriptStream, System.out, System.err);
        assertEquals(String.format(expectedOutFormat, script, "CLI/GroovySh", "null"), altStdout.toString().trim());
    }

    private class DummyScriptUsageListener implements ScriptListener {
        @Override
        public void onScriptExecution(String script, Binding binding, Object feature, Object context, String description, User u) {
            String username = "null";
            if (u != null) {
                username = u.getFullName();
            }
            ps.println(String.format(expectedOutFormat, script, context, username));
        }
    }
}
