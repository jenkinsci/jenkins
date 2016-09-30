package hudson.util;

import hudson.WebAppMain;
import jenkins.util.groovy.GroovyHookScript;
import org.kohsuke.stapler.WebApp;

import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        LOGGER.log(Level.SEVERE, "Failed to initialize Jenkins",this);

        WebApp.get(context).setApp(this);
        if (home == null) {
            return;
        }
        new GroovyHookScript("boot-failure", context, home, BootFailure.class.getClassLoader())
                .bind("exception",this)
                .bind("home",home)
                .bind("servletContext", context)
                .bind("attempts",loadAttempts(home))
                .run();
    }

    /**
     * Parses the boot attempt file carefully so as not to cause the entire hook script to fail to execute.
     */
    protected List<Date> loadAttempts(File home) {
        List<Date> dates = new ArrayList<Date>();
        if (home!=null) {
            File f = getBootFailureFile(home);
            try {
                if (f.exists()) {
                    try (BufferedReader failureFileReader = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line=failureFileReader.readLine())!=null) {
                            try {
                                dates.add(new Date(line));
                            } catch (Exception e) {
                                // ignore any parse error
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,"Failed to parse "+f,e);
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
