package hudson.tasks.test;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.tasks.junit.JUnitResultArchiver;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Project action object from test reporter, such as {@link JUnitResultArchiver},
 * which displays the trend report on the project top page.
 *
 * <p>
 * This works with any {@link AbstractTestResultAction} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultProjectAction implements Action {
    /**
     * Project that owns this action.
     */
    public final AbstractProject<?,?> project;

    public TestResultProjectAction(AbstractProject<?,?> project) {
        this.project = project;
    }

    /**
     * No task list item.
     */
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Test Report";
    }

    public String getUrlName() {
        return "test";
    }

    protected AbstractTestResultAction getLastTestResultAction() {
        AbstractBuild<?,?> b = project.getLastSuccessfulBuild();
        if(b!=null) {
            AbstractTestResultAction a = b.getTestResultAction();
            if(a!=null) return a;
        }
        return null;
    }

    /**
     * Display the test result trend.
     */
    public void doTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if(a!=null)
            a.doGraph(req,rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Generates the clickable map HTML fragment for {@link #doTrend(StaplerRequest, StaplerResponse)}.
     */
    public void doTrendMap( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        AbstractTestResultAction a = getLastTestResultAction();
        if(a!=null)
            a.doGraphMap(req,rsp);
        else
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Changes the test result report display mode.
     */
    public void doFlipTrend( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        boolean failureOnly = false;

        // check the current preference value
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(FAILURE_ONLY_COOKIE))
                    failureOnly = Boolean.parseBoolean(cookie.getValue());
            }
        }

        // flip!
        failureOnly = !failureOnly;

        // set the updated value
        Cookie cookie = new Cookie(FAILURE_ONLY_COOKIE,String.valueOf(failureOnly));
        List anc = req.getAncestors();
        Ancestor a = (Ancestor) anc.get(anc.size()-2);
        cookie.setPath(a.getUrl()); // just for this project
        cookie.setMaxAge(60*60*24*365); // 1 year
        rsp.addCookie(cookie);

        // back to the project page
        rsp.sendRedirect("..");
    }

    private static final String FAILURE_ONLY_COOKIE = "TestResultAction_failureOnly";
}
