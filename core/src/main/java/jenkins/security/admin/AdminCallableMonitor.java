package jenkins.security.admin;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AdministrativeMonitor;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Report any rejected {@link Callable}s and {@link FilePath} executions and allow
 * admins to whitelist them.
 *
 * @since 1.THU
 * @author Kohsuke Kawaguchi
 */
@Extension
public class AdminCallableMonitor extends AdministrativeMonitor {
    @Inject
    Jenkins jenkins;

    @Inject
    AdminCallableWhitelist whitelist;

    private interface OpMatcher {
        boolean matches(String op);
    }

    private class FilePathRule {
        final Pattern path;
        final OpMatcher op;
        final boolean allow;

        private FilePathRule(Pattern path, OpMatcher op, boolean allow) {
            this.path = path;
            this.op = op;
            this.allow = allow;
        }
    }

    private final List<FilePathRule> filePathRules = new ArrayList<FilePathRule>();

    public boolean checkFileAccess(String op, File path) throws SecurityException {
        String pathStr = null;

        for (FilePathRule rule : filePathRules) {
            if (rule.op.matches(op)) {
                if (pathStr==null)
                    // do not canonicalize nor absolutize, so that JENKINS_HOME that spans across
                    // multiple volumes via symlinks can look logically like one unit.
                    pathStr = path.getPath();

                if (rule.path.matcher(pathStr).matches()) {
                    // exclusion rule is only to bypass later path rules within #filePathRules,
                    // and we still want other FilePathFilters to whitelist/blacklist access.
                    // therefore I'm not throwing a SecurityException here
                    return rule.allow;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isActivated() {
        return whitelist.hasRejection();
    }

    @Override
    public String getDisplayName() {
        return "Slave \u2192 Master Command Whitelisting";
    }

    // bind this to URL
    public AdminCallableWhitelist getWhitelist() {
        return whitelist;
    }

    /**
     * Depending on whether the user said "examin" or "dismiss", send him to the right place.
     */
    public HttpResponse doAct(@QueryParameter String dismiss) throws IOException {
        if(dismiss!=null) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectTo("whitelist/");
        }
    }
}
