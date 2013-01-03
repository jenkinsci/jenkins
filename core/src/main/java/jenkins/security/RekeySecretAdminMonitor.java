package jenkins.security;

import hudson.Extension;
import hudson.console.AnnotatedLargeText;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AdministrativeMonitor;
import hudson.util.HttpResponses;
import hudson.util.SecretRewriter;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.util.io.FileBoolean;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Warns the administrator to run {@link SecretRewriter}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class RekeySecretAdminMonitor extends AdministrativeMonitor implements StaplerProxy {

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

    /**
     * Set to non-null once the rewriting activities starts running.
     */
    private volatile RekeyThread rekeyThread;


    public RekeySecretAdminMonitor() throws IOException {
        // if JENKINS_HOME existed <1.497, we need to offer rewrite
        // this computation needs to be done and the value be captured,
        // since $JENKINS_HOME/config.xml can be saved later before the user has
        // actually rewritten XML files.
        if (Jenkins.getInstance().isUpgradedFromBefore(new VersionNumber("1.496.*")))
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
            synchronized (this) {
                if (!isRewriterActive()) {
                    rekeyThread = new RekeyThread();
                    rekeyThread.start();
                }
            }
        } else
        if(req.hasParameter("schedule")) {
            scanOnBoot.on();
        } else
        if(req.hasParameter("dismiss")) {
            disable(true);
        } else
            throw HttpResponses.error(400,"Invalid request submission");

        return HttpResponses.redirectViaContextPath("/manage");
    }

    /**
     * Is there an active rewriting process going on?
     */
    public boolean isRewriterActive() {
        return rekeyThread !=null && rekeyThread.isAlive();
    }

    /**
     * Used to URL-bind {@link AnnotatedLargeText}.
     */
    public AnnotatedLargeText getLogText() {
        return new AnnotatedLargeText<RekeySecretAdminMonitor>(getLogFile(), Charset.defaultCharset(),
                !isRewriterActive(),this);
    }

    private static FileBoolean state(String name) {
        return new FileBoolean(new File(getBaseDir(),name));
    }

    @Initializer(fatal=false,after=InitMilestone.PLUGINS_STARTED,before=InitMilestone.EXTENSIONS_AUGMENTED)
    // as early as possible, but this needs to be late enough that the ConfidentialStore is available
    public static void scanOnReboot() throws InterruptedException, IOException, GeneralSecurityException {
        FileBoolean flag = new RekeySecretAdminMonitor().scanOnBoot;
        if (flag.isOn()) {
            flag.off();
            RekeyThread t = new RekeyThread();
            t.start();
            t.join();
            // block the boot until the rewrite process is complete
            // don't let the failure in RekeyThread block Jenkins boot.
        }
    }

    /**
     * Rewrite log file.
     */
    public static File getLogFile() {
        return new File(getBaseDir(),"rekey.log");
    }

    private static File getBaseDir() {
        return new File(Jenkins.getInstance().getRootDir(),RekeySecretAdminMonitor.class.getName());
    }

    private static class RekeyThread extends Thread {
        private final SecretRewriter rewriter;

        RekeyThread() throws GeneralSecurityException {
            super("Rekey secret thread");
            rewriter = new SecretRewriter(new File(getBaseDir(),"backups"));
        }

        @Override
        public void run() {
            try {
                LOGGER.info("Initiating a re-keying of secrets. See "+getLogFile());
                StreamTaskListener listener = new StreamTaskListener(getLogFile());
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
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Catastrophic failure to rewrite secrets",e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RekeySecretAdminMonitor.class.getName());

}
