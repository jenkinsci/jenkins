package hudson.slaves;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.InvalidObjectException;

import javax.servlet.ServletException;

import hudson.model.Descriptor;
import hudson.scheduler.CronTabList;
import hudson.util.FormFieldValidator;
import static hudson.Util.fixNull;
import hudson.Util;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import antlr.ANTLRException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since Jan 13, 2009 1:17:34 PM
 */
public class SimpleScheduledRetentionStrategy extends RetentionStrategy<SlaveComputer> {

    private static final Logger LOGGER = Logger.getLogger(SimpleScheduledRetentionStrategy.class.getName());

    private final String startTimeSpec;
    private transient CronTabList tabs;
    private transient Calendar lastChecked;
    private final long upTimeMins;

    @DataBoundConstructor
    public SimpleScheduledRetentionStrategy(String startTimeSpec, long upTimeMins) throws ANTLRException {
        this.startTimeSpec = startTimeSpec;
        this.tabs = CronTabList.create(startTimeSpec);
        this.lastChecked = new GregorianCalendar();
        this.upTimeMins = upTimeMins;
        this.lastChecked.add(Calendar.MINUTE, -1);
    }

    protected Object readResolve() throws ObjectStreamException {
        try {
            tabs = CronTabList.create(startTimeSpec);
            lastChecked = new GregorianCalendar();
            this.lastChecked.add(Calendar.MINUTE, -1);
        } catch (ANTLRException e) {
            InvalidObjectException x = new InvalidObjectException(e.getMessage());
            x.initCause(e);
            throw x;
        }
        return this;
    }

    public long check(SlaveComputer c) {
        if (c.isOffline()) {
            Calendar startUp = null;
            while (new Date().getTime() - lastChecked.getTimeInMillis() > 1000) {
                LOGGER.log(Level.FINE, "scheduled start checking {0}", lastChecked);
                if (tabs.check(lastChecked)) {
                    // need to ensure that we don't fire up after the upTime has been passed
                    // if we have an aggressive upTime, or a slow check
                    startUp = (Calendar) lastChecked.clone();
                }
                lastChecked.add(Calendar.MINUTE, 1);
            }
            if (startUp != null) {
                if (new Date().getTime() - startUp.getTimeInMillis() > 1000 * 60L * upTimeMins) {
                    LOGGER.log(Level.INFO, "Missed scheduled startup");
                } else {
                    LOGGER.log(Level.INFO, "Launching computer {0} per schedule", new Object[]{c.getName()});
                    if (c.isLaunchSupported()) {
                        c.connect(true);
                    }
                }
            }
            return upTimeMins;
        } else {
            if (new Date().getTime() - lastChecked.getTimeInMillis() > 1000 * 60L * upTimeMins) {
                LOGGER.log(Level.INFO, "Disconnecting computer {0} as it has finished its scheduled uptime",
                        new Object[]{c.getName()});
                c.disconnect();
            }
            return 1;
        }
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public Descriptor<RetentionStrategy<?>> getDescriptor() {
        return DESCRIPTOR;
    }

    private static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {

        /**
         * Constructs a new DescriptorImpl.
         */
        public DescriptorImpl() {
            super(Demand.class);
        }

        public String getDisplayName() {
            return Messages.SimpleScheduledRetentionStrategy_displayName();
        }

        /**
         * Performs syntax check.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // false==No permission needed for this syntax check
            new FormFieldValidator(req, rsp, false) {
                @Override
                protected void check() throws IOException, ServletException {
                    try {
                        String msg = CronTabList.create(fixNull(request.getParameter("value"))).checkSanity();
                        if (msg != null) {
                            warning(msg);
                        } else {
                            ok();
                        }
                    } catch (ANTLRException e) {
                        error(e.getMessage());
                    }
                }
            }.process();
        }
    }

    static {
        LIST.add(DESCRIPTOR);
    }

}
