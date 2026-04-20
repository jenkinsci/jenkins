package hudson.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.WebAppMain;
import jakarta.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.groovy.GroovyHookScript;
import org.kohsuke.stapler.WebApp;

/**
 * Indicates a fatal boot problem, among {@link ErrorObject}
 *
 * @author Kohsuke Kawaguchi
 * @see WebAppMain#recordBootAttempt(File)
 */
public abstract class BootFailure extends ErrorObject {
    protected BootFailure() {
    }

    protected BootFailure(Throwable cause) {
        super(cause);
    }

    /**
     * Exposes this failure to UI and invoke the hook.
     *
     * @param home
     *      JENKINS_HOME if it's already known.
     */
    public void publish(ServletContext context, @CheckForNull File home) {
        LOGGER.log(Level.SEVERE, "Failed to initialize Jenkins", this);

        WebApp.get(context).setApp(this);
        if (home == null) {
            return;
        }
        new GroovyHookScript("boot-failure", context, home, BootFailure.class.getClassLoader())
                .bind("exception", this)
                .bind("home", home)
                .bind("servletContext", context)
                .bind("attempts", loadAttempts(home))
                .run();
        Jenkins.get().getLifecycle().onBootFailure(this);
    }

    /**
     * Parses the boot attempt file carefully so as not to cause the entire hook script to fail to execute.
     */
    protected List<Date> loadAttempts(File home) {
        List<Date> dates = new ArrayList<>();
        if (home != null) {
            File f = getBootFailureFile(home);
            try {
                if (f.exists()) {
                    try (BufferedReader failureFileReader = Files.newBufferedReader(f.toPath(), Charset.defaultCharset())) {
                        String line;
                        // WebAppMain.recordBootAttempt uses Date.toString when writing, so that is the format we must parse.
                        SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
                        while ((line = failureFileReader.readLine()) != null) {
                            try {
                                dates.add(df.parse(line));
                            } catch (Exception e) {
                                // ignore any parse error
                            }
                        }
                    }
                }
            } catch (IOException | InvalidPathException e) {
                LOGGER.log(Level.WARNING, "Failed to parse " + f, e);
            }
        }
        return dates;
    }

    private static final Logger LOGGER = Logger.getLogger(BootFailure.class.getName());

    /**
     * This file captures failed boot attempts.
     * Every time we try to boot, we add the timestamp to this file,
     * then when we boot, the file gets deleted.
     */
    public static File getBootFailureFile(File home) {
        return new File(home, "failed-boot-attempts.txt");
    }

}
