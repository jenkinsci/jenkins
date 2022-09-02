package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.ExtensionList;
import hudson.cli.GroovyCommand;
import hudson.cli.GroovyshCommand;
import hudson.model.User;
import hudson.util.RemotingDiagnostics;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import javax.servlet.RequestDispatcher;
import jenkins.util.ScriptListener;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class ScriptListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void consoleUsageIsLogged() throws Exception {
        RequestDispatcher view = mock(RequestDispatcher.class);
        StaplerRequest req = mock(StaplerRequest.class);
        StaplerResponse rsp = mock(StaplerResponse.class);

        final String output = "hello from script console";
        final String script = "println '" + output + "'";

        when(req.getMethod()).thenReturn("POST");
        when(req.getParameter("script")).thenReturn(script);
        when(req.getView(j.jenkins, "_scriptText.jelly")).thenReturn(view);
        j.jenkins.doScriptText(req, rsp);

        final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
        String execution = listener.getExecutionString();

        assertThat(execution, containsString(RemotingDiagnostics.class.getName()));
        assertThat(execution, containsString(script));
        assertThat(listener.getOutput(), containsString(output));
    }

    @Test
    public void groovyCliUsageIsLogged() throws Exception {
        GroovyCommand cmd = new GroovyCommand();
        cmd.script = "=";

        final String output = "hello from groovy CLI";
        final String script = "println '" + output + "'";

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        cmd.main(new ArrayList<>(), Locale.ENGLISH, scriptStream, System.out, System.err);

        final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
        String execution = listener.getExecutionString();

        assertThat(execution, containsString(GroovyCommand.class.getName()));
        assertThat(execution, containsString(script));
        assertThat(listener.getOutput(), containsString(output));
    }

    @Test
    public void groovyShCliUsageIsLogged() throws Exception {
        // TODO more comprehensive test of this
        GroovyshCommand cmd = new GroovyshCommand();

        final String output = "hello from groovysh CLI";
        final String script = "println '" + output + "'";

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());

        cmd.main(new ArrayList<>(), Locale.ENGLISH, scriptStream, System.out, System.err);

        final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
        String execution = listener.getExecutionString();

        assertThat(execution, containsString(GroovyshCommand.class.getName()));
        assertThat(execution, containsString(script));
        assertThat(listener.getOutput(), containsString(output));
    }

    @TestExtension
    public static class DummyScriptUsageListener implements ScriptListener {
        private final StringBuilder script = new StringBuilder();
        private final StringBuilder output = new StringBuilder();

        @Override
        public void onScriptExecution(String script, Binding binding, @NonNull Object feature, Object context, @NonNull String correlationId, User u) {
            String username = "null";
            if (u != null) {
                username = u.getFullName();
            }
            String expectedOutFormat = "Script: '%s' in '%s' with '%s' by '%s'";
            this.script.append(String.format(expectedOutFormat, script, feature, context, correlationId, username)).append("\n");
        }

        @Override
        public void onScriptOutput(String output, @NonNull Object feature, Object context, @NonNull String correlationId, User user) {
            this.output.append(output);
        }

        String getExecutionString() {
            return script.toString();
        }

        String getOutput() {
            return output.toString();
        }
    }
}
