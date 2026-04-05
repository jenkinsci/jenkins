package jenkins.model;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.stream.Collectors;
import jenkins.model.Jenkins.MasterComputer;
import org.jenkinsci.Symbol;

/**
 * {@link EnvironmentContributor} that adds the basic set of environment variables that
 * we've been exposing historically.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = -100) @Symbol("core")
public class CoreEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(Run r, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Computer c = Computer.currentComputer();
        if (c != null) {
            EnvVars compEnv = c.getEnvironment().overrideAll(env);
            env.putAll(compEnv);
        }
        env.put("BUILD_DISPLAY_NAME", r.getDisplayName());

        Jenkins j = Jenkins.get();
        String rootUrl = j.getRootUrl();
        if (rootUrl != null) {
            env.put("BUILD_URL", rootUrl + r.getUrl());
        }
    }

    @Override
    public void buildEnvironmentFor(Job j, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        env.put("CI", "true");

        Jenkins jenkins = Jenkins.get();
        String rootUrl = jenkins.getRootUrl();
        if (rootUrl != null) {
            env.put("JENKINS_URL", rootUrl);
            env.put("HUDSON_URL", rootUrl); // Legacy compatibility
            env.put("JOB_URL", rootUrl + j.getUrl());
        }

        String root = jenkins.getRootDir().getPath();
        env.put("JENKINS_HOME", root);
        env.put("HUDSON_HOME", root);   // legacy compatibility

        Thread t = Thread.currentThread();
        if (t instanceof Executor e) {
            env.put("EXECUTOR_NUMBER", String.valueOf(e.getNumber()));
            if (e.getOwner() instanceof MasterComputer) {
                env.put("NODE_NAME", Jenkins.get().getSelfLabel().getName());
            } else {
                env.put("NODE_NAME", e.getOwner().getName());
            }
            Node n = e.getOwner().getNode();
            if (n != null)
                env.put("NODE_LABELS", n.getAssignedLabels().stream().map(Object::toString).collect(Collectors.joining(" ")));
        }
    }
}
