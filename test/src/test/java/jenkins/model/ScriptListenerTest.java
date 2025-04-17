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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ScriptListenerTest {

    private final LogRecorder logging = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void consoleUsageIsLogged() throws IOException {
        final String output = "hello from script console";
        final String script = "println '" + output + "'";

        logging.record(DefaultScriptListener.class.getName(), Level.FINEST).capture(100);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final WebRequest request = new WebRequest(new URL(wc.getContextPath() + "scriptText?script=" + script), HttpMethod.POST);
            wc.getPage(wc.addCrumb(request));
        }

        { // DefaultScriptListener
            final List<String> messages = logging.getMessages();
            assertThat(messages, hasSize(2));

            assertThat(messages.get(0), containsString("Execution of script: '" + script + "' with binding: '[:]' in feature: 'class hudson.util.RemotingDiagnostics' and context: 'hudson.remoting.LocalChannel@"));
            assertThat(messages.get(0), containsString("' with correlation: '"));
            assertThat(messages.get(0), containsString("' (no user)"));

            assertThat(messages.get(1), containsString("Script output: 'hello from script console" + System.lineSeparator() + "' in feature: 'class hudson.util.RemotingDiagnostics' and context: 'hudson.remoting.LocalChannel@"));
            assertThat(messages.get(1), containsString("' with correlation: '"));
            assertThat(messages.get(1), containsString("' (no user)"));
        }

        { // DummyScriptUsageListener
            final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
            String execution = listener.getExecutionString();

            assertThat(execution, containsString(RemotingDiagnostics.class.getName()));
            assertThat(execution, containsString(script));
            assertThat(listener.getOutput(), containsString(output));
        }
    }

    @Test
    void groovyCliUsageIsLogged() {
        final String output = "hello from groovy CLI";
        final String script = "println '" + output + "'";

        logging.record(DefaultScriptListener.class.getName(), Level.FINEST).capture(100);

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        final CLICommandInvoker.Result result = new CLICommandInvoker(j, "groovy").withArgs("=").withStdin(scriptStream).invoke();
        final String stdout = result.stdout();
        assertThat(stdout, containsString("hello from groovy CLI"));

        { // DefaultScriptListener
            final List<String> messages = logging.getMessages();
            assertThat(messages, hasSize(3));

            assertThat(messages.get(0), containsString("Execution of script: '" + script + "' with binding: '["));
            assertThat(messages.get(0), containsString("]' in feature: 'class hudson.cli.GroovyCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(0), containsString("' (no user)"));

            assertThat(messages.get(1), containsString("Script output: 'hello from groovy CLI' in feature: 'class hudson.cli.GroovyCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(1), containsString("' (no user)"));

            assertThat(messages.get(2), containsString("Script output: '" + System.lineSeparator() + "' in feature: 'class hudson.cli.GroovyCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(2), containsString("' (no user)"));
        }

        { // DummyScriptUsageListener
            final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
            String execution = listener.getExecutionString();

            assertThat(execution, containsString(GroovyCommand.class.getName()));
            assertThat(execution, containsString(script));
            assertThat(listener.getOutput(), containsString(output));
        }
    }

    @Test
    void groovyShCliUsageIsLogged() {
        final String output = "hello from groovysh CLI";
        final String script = "println '" + output + "'";

        logging.record(DefaultScriptListener.class.getName(), Level.FINEST).capture(100);

        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        new CLICommandInvoker(j, "groovysh").withStdin(scriptStream).invoke();

        { // DefaultScriptListener
            final List<String> messages = logging.getMessages();
            assertThat(messages, hasSize(9));

            assertThat(messages.get(0), containsString("Execution of script: 'null' with binding: 'null' in feature: 'class hudson.cli.GroovyshCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(0), containsString("' (no user)"));

            // Only match short substrings to not have to deal with color escape codes in the output
            assertThat(messages.get(1), containsString("Groovy Shell")); // Groovy Shell (2.4.21, JVM: 11.0.15)
            assertThat(messages.get(2), containsString(":help")); // Type ':help' or ':h' for help.
            assertThat(messages.get(3), containsString("Script output: '-------------------"));
            assertThat(messages.get(4), containsString("000")); // groovy:000>

            assertThat(messages.get(5), containsString("Execution of script: '" + script + "' with binding: '["));
            assertThat(messages.get(5), containsString("]' in feature: 'class hudson.cli.GroovyshCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(5), containsString("' (no user)"));

            assertThat(messages.get(6), containsString("Script output: 'hello from groovysh CLI" + System.lineSeparator() + "' in feature: 'class hudson.cli.GroovyshCommand' and context: 'null' with correlation: '"));
            assertThat(messages.get(6), containsString("' (no user)"));

            // Only match short substrings to not have to deal with color escape codes in the output
            assertThat(messages.get(7), containsString("===>")); // ===> null
            assertThat(messages.get(8), containsString("000")); // groovy:000>
        }

        { // DummyScriptUsageListener
            final DummyScriptUsageListener listener = ExtensionList.lookupSingleton(DummyScriptUsageListener.class);
            String execution = listener.getExecutionString();

            assertThat(execution, containsString(GroovyshCommand.class.getName()));
            assertThat(execution, containsString(script));
            assertThat(listener.getOutput(), containsString(output));
        }
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
