package hudson.cli

import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.BuildListener
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.ParameterDefinition
import hudson.model.Result
import hudson.model.StringParameterDefinition
import hudson.tasks.Shell
import jenkins.model.JenkinsLocationConfiguration
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.BuildWatcher
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestBuilder

/**
 * @author Kohsuke Kawaguchi
 */
public class SetBuildParameterCommandTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void cli()  {
        JenkinsLocationConfiguration.get().url = j.URL;

        def p = j.createFreeStyleProject();
        p.buildersList.add(new TestBuilder() {
            @Override
            boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                def jar = j.jenkins.servletContext.getResource("/WEB-INF/jenkins-cli.jar")
                build.workspace.child("cli.jar").copyFrom(jar);

                return true;
            }
        });
        List<ParameterDefinition> pd = [new StringParameterDefinition("a", ""), new StringParameterDefinition("b", "")];
        p.addProperty(new ParametersDefinitionProperty(pd))
        p.buildersList.add(new Shell("java -jar cli.jar -remoting -noKeyAuth set-build-parameter a b"))
        p.buildersList.add(new Shell("java -jar cli.jar -remoting -noKeyAuth set-build-parameter a x"))
        p.buildersList.add(new Shell("java -jar cli.jar -remoting -noKeyAuth set-build-parameter b y"))

        def r = [:];

        def b = j.assertBuildStatusSuccess(p.scheduleBuild2(0))
        b.getAction(ParametersAction.class).parameters.each { v -> r[v.name]=v.value }

        assert r.equals(["a":"x", "b":"y"]);

        p.buildersList.add(new Shell("BUILD_NUMBER=1 java -jar cli.jar -remoting -noKeyAuth set-build-parameter a b"))
        def b2 = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertLogContains("#1 is not currently being built", b2)
        r = [:];
        b.getAction(ParametersAction.class).parameters.each { v -> r[v.name]=v.value }
        assert r.equals(["a":"x", "b":"y"]);
    }
}
