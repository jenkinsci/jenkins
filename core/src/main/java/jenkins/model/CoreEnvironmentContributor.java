package jenkins.model;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins.MasterComputer;

import java.io.IOException;

/**
 * {@link EnvironmentContributor} that adds the basic set of environment variables that
 * we've been exposing historically.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100)
public class CoreEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(Run r, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Computer c = Computer.currentComputer();
        if (c!=null){
            EnvVars compEnv = c.getEnvironment().overrideAll(env);
            env.putAll(compEnv);
        }
        env.put("BUILD_DISPLAY_NAME",r.getDisplayName());

        Jenkins j = Jenkins.getInstance();
        String rootUrl = j.getRootUrl();
        if(rootUrl!=null) {
            env.put("BUILD_URL", rootUrl+r.getUrl());
        }
    }

    @Override
    public void buildEnvironmentFor(Job j, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstance();
        String rootUrl = jenkins.getRootUrl();
        if(rootUrl!=null) {
            env.put("JENKINS_URL", rootUrl);
            env.put("HUDSON_URL", rootUrl); // Legacy compatibility
            env.put("JOB_URL", rootUrl+j.getUrl());
        }

        String root = jenkins.getRootDir().getPath();
        env.put("JENKINS_HOME", root);
        env.put("HUDSON_HOME", root);   // legacy compatibility

        Thread t = Thread.currentThread();
        if (t instanceof Executor) {
            Executor e = (Executor) t;
            env.put("EXECUTOR_NUMBER", String.valueOf(e.getNumber()));
            if (e.getOwner() instanceof MasterComputer) {
                env.put("NODE_NAME", "master");
            } else {
                env.put("NODE_NAME", e.getOwner().getName());
            }
            Node n = e.getOwner().getNode();
            if (n != null)
                env.put("NODE_LABELS", Util.join(n.getAssignedLabels(), " "));
        }
    }
}
