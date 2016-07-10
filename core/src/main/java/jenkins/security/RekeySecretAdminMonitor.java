package jenkins.security;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.TaskListener;
import hudson.util.HttpResponses;
import hudson.util.SecretRewriter;
import hudson.util.VersionNumber;
import jenkins.management.AsynchronousAdministrativeMonitor;
import jenkins.model.Jenkins;
import jenkins.util.io.FileBoolean;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Warns the administrator to run {@link SecretRewriter}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("rekeySecret")
public class RekeySecretAdminMonitor extends AsynchronousAdministrativeMonitor implements StaplerProxy {

    /**
     * Whether we detected a need to run the rewrite program.
     * Once we set it to true, we'll never turn it off.
     *
     * If the admin decides to dismiss this warning, we use {@link #isEnabled()} for that.
     *
     * In this way we can correctly differentiate all the different states.
     */
    private final FileBoolean needed = state("needed");

    /**
     * If the scanning process has run to the completion, we set to this true.
     */
    private final FileBoolean done = state("done");

    /**
     * If the rewrite process is scheduled upon the next boot.
     */
    private final FileBoolean scanOnBoot = state("scanOnBoot");

    public RekeySecretAdminMonitor() throws IOException {
        // if JENKINS_HOME existed <1.497, we need to offer rewrite
        // this computation needs to be done and the value be captured,
        // since $JENKINS_HOME/config.xml can be saved later before the user has
        // actually rewritten XML files.
        Jenkins j = Jenkins.getInstance();
        if (j.isUpgradedFromBefore(new VersionNumber("1.496.*"))
        &&  new FileBoolean(new File(j.getRootDir(),"secret.key.not-so-secret")).isOff())
            needed.on();
    }

    /**
     * Requires ADMINISTER permission for any operation in here.
     */
    public Object getTarget() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    @Override
    public boolean isActivated() {
        return needed.isOn();
    }

    /**
     * Indicates that the re-keying has run to the completion.
     */
    public boolean isDone() {
        return done.isOn();
    }

    public void setNeeded() {
        needed.on();
    }

    public boolean isScanOnBoot() {
        return scanOnBoot.isOn();
    }

    @RequirePOST
    public HttpResponse doScan(StaplerRequest req) throws IOException, GeneralSecurityException {
        if(req.hasParameter("background")) {
            start(false);
        } else
        if(req.hasParameter("schedule")) {
            scanOnBoot.on();
        } else
        if(req.hasParameter("dismiss")) {
            disable(true);
        } else
            throw HttpResponses.error(400,"Invalid request submission: " + req.getParameterMap());

        return HttpResponses.redirectViaContextPath("/manage");
    }


    private FileBoolean state(String name) {
        return new FileBoolean(new File(getBaseDir(),name));
    }

    @Initializer(fatal=false,after=InitMilestone.PLUGINS_STARTED,before=InitMilestone.EXTENSIONS_AUGMENTED)
    // as early as possible, but this needs to be late enough that the ConfidentialStore is available
    public void scanOnReboot() throws InterruptedException, IOException, GeneralSecurityException {
        FileBoolean flag = scanOnBoot;
        if (flag.isOn()) {
            flag.off();
            start(false).join();
            // block the boot until the rewrite process is complete
            // don't let the failure in RekeyThread block Jenkins boot.
        }
    }

    @Override
    public String getDisplayName() {
        return Messages.RekeySecretAdminMonitor_DisplayName();
    }

    /**
     * Rewrite log file.
     */
    @Override
    protected File getLogFile() {
        return new File(getBaseDir(),"rekey.log");
    }

    @Override
    protected void fix(TaskListener listener) throws Exception {
        LOGGER.info("Initiating a re-keying of secrets. See "+getLogFile());

        SecretRewriter rewriter = new SecretRewriter(new File(getBaseDir(),"backups"));

        try {
            PrintStream log = listener.getLogger();
            log.println("Started re-keying " + new Date());
            int count = rewriter.rewriteRecursive(Jenkins.getInstance().getRootDir(), listener);
            log.printf("Completed re-keying %d files on %s\n",count,new Date());
            new RekeySecretAdminMonitor().done.on();
            LOGGER.info("Secret re-keying completed");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal failure in re-keying secrets",e);
            e.printStackTrace(listener.error("Fatal failure in rewriting secrets"));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RekeySecretAdminMonitor.class.getName());

}
