package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.ExtensionList;
import hudson.cli.CLICommandInvoker;
import hudson.cli.GroovyCommand;
import hudson.cli.GroovyshCommand;
import hudson.model.User;
import hudson.util.RemotingDiagnostics;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import jenkins.util.DefaultScriptListener;
import jenkins.util.ScriptListener;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class ScriptListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Test
    public void consoleUsageIsLogged() throws IOException {
        final String output = "hello from script console";
        final String script = "println '" + output + "'";

        logging.record(DefaultScriptListener.class.getName(), Level.FINEST).capture(100);

        final JenkinsRule.WebClient wc = j.createWebClient();
        final WebRequest request = new WebRequest(new URL(wc.getContextPath() + "scriptText?script=" + script), HttpMethod.POST);
        wc.getPage(wc.addCrumb(request));

        final List<String> messages = logging.getMessages();
        assertThat(messages, hasSize(2));

        assertThat(messages.get(0), containsString("Execution of script: '" + script + "' with binding: '[:]' in feature: 'class hudson.util.RemotingDiagnostics' and context: 'hudson.remoting.LocalChannel@"));
        assertThat(messages.get(0), containsString("' with correlation: '"));
        assertThat(messages.get(0), containsString("' by user: 'null'"));

        assertThat(messages.get(1), containsString("Script output: 'hello from script console\n' in feature: 'class hudson.util.RemotingDiagnostics' and context: 'hudson.remoting.LocalChannel@"));
        assertThat(messages.get(1), containsString("' with correlation: '"));
        assertThat(messages.get(1), containsString("' for user: 'null'"));

        final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
        String execution = listener.getExecutionString();

        assertThat(execution, containsString(RemotingDiagnostics.class.getName()));
        assertThat(execution, containsString(script));
        assertThat(listener.getOutput(), containsString(output));
    }

    @Test
    public void groovyCliUsageIsLogged() {
        final String output = "hello from groovy CLI";
        final String script = "println '" + output + "'";

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        new CLICommandInvoker(j, "groovy").withArgs("=").withStdin(scriptStream).invoke();

        final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
        String execution = listener.getExecutionString();

        assertThat(execution, containsString(GroovyCommand.class.getName()));
        assertThat(execution, containsString(script));
        assertThat(listener.getOutput(), containsString(output));
    }

    @Test
    public void groovyShCliUsageIsLogged() {
        // TODO more comprehensive test of this
        final String output = "hello from groovysh CLI";
        final String script = "println '" + output + "'";

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        new CLICommandInvoker(j, "groovysh").withStdin(scriptStream).invoke();

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
