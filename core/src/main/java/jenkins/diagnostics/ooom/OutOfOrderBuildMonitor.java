package jenkins.diagnostics.ooom;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.HttpResponses;
import jenkins.management.AsynchronousAdministrativeMonitor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reports any {@link Problem}s found and report them in the "Manage Jenkins" page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class OutOfOrderBuildMonitor extends AsynchronousAdministrativeMonitor {
    private final Set<Problem> problems = Collections.synchronizedSet(new LinkedHashSet<Problem>());

    @Override
    public boolean isActivated() {
        return !problems.isEmpty() || getLogFile().exists();
    }

    void addProblem(Problem p) {
        problems.add(p);
    }

    public Set<Problem> getProblems() {
        return Collections.unmodifiableSet(new LinkedHashSet<Problem>(problems));
    }

    @RequirePOST
    public HttpResponse doFix() {
        start(false);
        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Discards the current log file so that the "stuff is completed" message will be gone.
     */
    @RequirePOST
    public HttpResponse doDismiss() {
        getLogFile().delete();
        return HttpResponses.forwardToPreviousPage();
    }

    @Override
    public String getDisplayName() {
        return "Fix Out-of-order Builds";
    }

    @Override
    public File getLogFile() {
        return super.getLogFile();
    }

    @Override
    protected void fix(TaskListener listener) throws Exception {
        Set<Problem> problems = getProblems();
        for (Problem problem : problems) {
            problem.fix(listener);
        }
        this.problems.removeAll(problems);
    }
}
