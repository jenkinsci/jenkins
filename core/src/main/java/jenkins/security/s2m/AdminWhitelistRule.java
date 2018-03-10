package jenkins.security.s2m;

import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import jenkins.util.io.FileBoolean;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleSensitive;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Rules of whitelisting for {@link RoleSensitive} objects and {@link FilePath}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class AdminWhitelistRule implements StaplerProxy {
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

    private final Jenkins jenkins;

    private boolean masterKillSwitch;

    public AdminWhitelistRule() throws IOException, InterruptedException {
        this.jenkins = Jenkins.getInstance();

        // while this file is not a secret, write access to this file is dangerous,
        // so put this in the better-protected part of $JENKINS_HOME, which is in secrets/

        // overwrite 30-default.conf with what we think is the best from the core.
        // this file shouldn't be touched by anyone. For local customization, use other files in the conf dir.
        // 0-byte file is used as a signal from the admin to prevent this overwriting
        placeDefaultRule(
                new File(jenkins.getRootDir(), "secrets/whitelisted-callables.d/default.conf"),
                getClass().getResourceAsStream("callable.conf"));
        placeDefaultRule(
                new File(jenkins.getRootDir(), "secrets/filepath-filters.d/30-default.conf"),
                transformForWindows(getClass().getResourceAsStream("filepath-filter.conf")));

        this.whitelisted = new CallableWhitelistConfig(
                new File(jenkins.getRootDir(),"secrets/whitelisted-callables.d/gui.conf"));
        this.rejected = new CallableRejectionConfig(
                new File(jenkins.getRootDir(),"secrets/rejected-callables.txt"),
                whitelisted);
        this.filePathRules = new FilePathRuleConfig(
                new File(jenkins.getRootDir(),"secrets/filepath-filters.d/50-gui.conf"));
        this.masterKillSwitch = loadMasterKillSwitchFile();
    }

    /**
     * Reads the master kill switch.
     *
     * Instead of {@link FileBoolean}, we use a text file so that the admin can prevent Jenkins from
     * writing this to file.
     */
    private boolean loadMasterKillSwitchFile() {
        File f = getMasterKillSwitchFile();
        try {
            if (!f.exists())    return true;
            return Boolean.parseBoolean(FileUtils.readFileToString(f).trim());
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to read "+f, e);
            return false;
        }
    }

    private File getMasterKillSwitchFile() {
        return new File(jenkins.getRootDir(),"secrets/slave-to-master-security-kill-switch");
    }

    /**
     * Transform path for Windows.
     */
    private InputStream transformForWindows(InputStream src) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(src));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream p = new PrintStream(out)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#") && Functions.isWindows())
                    line = line.replace("/", "\\\\");
                p.println(line);
            }
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void placeDefaultRule(File f, InputStream src) throws IOException, InterruptedException {
        try {
            new FilePath(f).copyFrom(src);
        } catch (IOException e) {
            // we allow admins to create a read-only file here to block overwrite,
            // so this can fail legitimately
            if (!f.canWrite())  return;
            LOGGER.log(WARNING, "Failed to generate "+f,e);
        }
    }

    public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
        if (masterKillSwitch)
            return true;    // master kill switch is on. subsystem deactivated

        String name = subject.getClass().getName();

        if (whitelisted.contains(name))
            return true;    // whitelisted by admin

        // otherwise record the problem and refuse to execute that
        rejected.report(subject.getClass());
        return false;
    }

    public boolean checkFileAccess(String op, File f) {
        // if the master kill switch is off, we allow everything
        if (masterKillSwitch)
            return true;

        return filePathRules.checkFileAccess(op, f);
    }

    @RequirePOST
    public HttpResponse doSubmit(StaplerRequest req) throws IOException {
        jenkins.checkPermission(Jenkins.RUN_SCRIPTS);

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

        String newRules = Util.fixNull(req.getParameter("filePathRules"));
        filePathRules.parseTest(newRules);  // test first before writing a potentially broken rules
        filePathRules.set(newRules);

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

    public boolean getMasterKillSwitch() {
        return masterKillSwitch;
    }

    public void setMasterKillSwitch(boolean state) {
        try {
            jenkins.checkPermission(Jenkins.RUN_SCRIPTS);
            FileUtils.writeStringToFile(getMasterKillSwitchFile(),Boolean.toString(state));
            // treat the file as the canonical source of information in case write fails
            masterKillSwitch = loadMasterKillSwitchFile();
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to write master kill switch", e);
        }
    }

    /**
     * Restricts the access to administrator.
     */
    @Override
    public Object getTarget() {
        jenkins.checkPermission(Jenkins.RUN_SCRIPTS);
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger(AdminWhitelistRule.class.getName());
}
