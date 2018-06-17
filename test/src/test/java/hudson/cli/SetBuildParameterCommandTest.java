package hudson.cli;

import com.google.common.collect.ImmutableMap;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.ParameterDefinition;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

public class SetBuildParameterCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void cli() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                URL jar = j.jenkins.servletContext.getResource("/WEB-INF/jenkins-cli.jar");
                build.getWorkspace().child("cli.jar").copyFrom(jar);

                return true;
            }
        });
        List<ParameterDefinition> pd = Arrays.asList(new StringParameterDefinition("a", ""), new StringParameterDefinition("b", ""));
        p.addProperty(new ParametersDefinitionProperty(pd));
        p.getBuildersList().add(createScriptBuilder("java -jar cli.jar -remoting -noKeyAuth set-build-parameter a b"));
        p.getBuildersList().add(createScriptBuilder("java -jar cli.jar -remoting -noKeyAuth set-build-parameter a x"));
        p.getBuildersList().add(createScriptBuilder("java -jar cli.jar -remoting -noKeyAuth set-build-parameter b y"));

        Map<String, Object> r = new TreeMap<>();

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.getAction(ParametersAction.class).getParameters().forEach(v -> r.put(v.getName(), v.getValue()));

        assertEquals(ImmutableMap.of("a", "x", "b", "y"), r);

        if (Functions.isWindows()) {
            p.getBuildersList().add(new BatchFile("set BUILD_NUMBER=1\r\njava -jar cli.jar -remoting -noKeyAuth set-build-parameter a b"));
        } else {
            p.getBuildersList().add(new Shell("BUILD_NUMBER=1 java -jar cli.jar -remoting -noKeyAuth set-build-parameter a b"));
        }
        FreeStyleBuild b2 = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertLogContains("#1 is not currently being built", b2);
        r.clear();
        b.getAction(ParametersAction.class).getParameters().forEach(v -> r.put(v.getName(), v.getValue()));
        assertEquals(ImmutableMap.of("a", "x", "b", "y"), r);
    }

    //TODO: determine if this should be pulled out into JenkinsRule or something
    /**
     * Create a script based builder (either Shell or BatchFile) depending on
     * platform
     * @param script the contents of the script to run
     * @return A Builder instance of either Shell or BatchFile
     */
    private Builder createScriptBuilder(String script) {
        return Functions.isWindows() ? new BatchFile(script) : new Shell(script);
    }

}
