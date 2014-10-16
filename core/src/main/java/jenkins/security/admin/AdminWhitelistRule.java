package jenkins.security.admin;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleSensitive;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.logging.Logger;

/**
 * Rules of whitelisting for {@link RoleSensitive} objects and {@link FilePath}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class AdminWhitelistRule implements StaplerProxy {
    @Inject
    Jenkins jenkins;

    /**
     * Ones that we rejected but want to run by admins.
     */
    public final CallableRejectionConfig rejected;

    /**
     * Callables that admins have whitelisted explicitly.
     */
    public final CallableWhitelistConfig whitelisted;

    /**
     * FilePath access pattern rules specified by the admin
     */
    public final FilePathRuleConfig filePathRules;

    public AdminWhitelistRule() {
        // while this file is not a secret, write access to this file is dangerous,
        // so put this in the better-protected part of $JENKINS_HOME, which is in secrets/
        this.whitelisted = new CallableWhitelistConfig(
                new File(jenkins.getRootDir(),"secrets/whitelisted-callables.txt"));
        this.rejected = new CallableRejectionConfig(
                new File(jenkins.getRootDir(),"secrets/rejected-callables.txt"),
                whitelisted);
        this.filePathRules = new FilePathRuleConfig(
                new File(jenkins.getRootDir(),"secrets/filepath-filter.txt"));
    }

    public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
        String name = subject.getClass().getName();

        if (whitelisted.contains(name))
            return true;    // whitelisted by admin

        // otherwise record the problem and refuse to execute that
        rejected.report(subject.getClass());
        return false;
    }

    @RequirePOST
    public HttpResponse doSubmit(StaplerRequest req) throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);

        String whitelist = Util.fixNull(req.getParameter("whitelist"));
        if (!whitelist.endsWith("\n"))
            whitelist+="\n";

        Enumeration e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            if (name.startsWith("class:")) {
                whitelist += name.substring(6)+"\n";
            }
        }

        whitelisted.set(whitelist);

        return HttpResponses.redirectToDot();
    }

    /**
     * Approves all the currently rejected subjects
     */
    @RequirePOST
    public HttpResponse doApproveAll() throws IOException {
        StringBuilder buf = new StringBuilder();
        for (Class c : rejected.get()) {
            buf.append(c.getName()).append('\n');
        }
        whitelisted.append(buf.toString());

        return HttpResponses.ok();
    }

    /**
     * Approves specific callables by their names.
     */
    @RequirePOST
    public HttpResponse doApprove(@QueryParameter String value) throws IOException {
        whitelisted.append(value);
        return HttpResponses.ok();
    }

    /**
     * Restricts the access to administrator.
     */
    @Override
    public Object getTarget() {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger(AdminWhitelistRule.class.getName());
}
