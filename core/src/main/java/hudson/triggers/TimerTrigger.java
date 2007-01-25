package hudson.triggers;

import antlr.ANTLRException;
import static hudson.Util.fixNull;
import hudson.model.Descriptor;
import hudson.scheduler.CronTabList;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * {@link Trigger} that runs a job periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimerTrigger extends Trigger {
    public TimerTrigger(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    protected void run() {
        project.scheduleBuild();
    }

    public Descriptor<Trigger> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<Trigger> DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<Trigger> {
        public DescriptorImpl() {
            super(TimerTrigger.class);
        }

        public String getDisplayName() {
            return "Build periodically";
        }

        public String getHelpFile() {
            return "/help/project-config/timer.html";
        }

        /**
         * Performs syntax check.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,true) {
                protected void check() throws IOException, ServletException {
                    try {
                        CronTabList.create(fixNull(request.getParameter("value")));
                        ok();
                    } catch (ANTLRException e) {
                        error(e.getMessage());
                    }
                }
            }.process();
        }

        public Trigger newInstance(StaplerRequest req) throws FormException {
            try {
                return new TimerTrigger(req.getParameter("timer_spec"));
            } catch (ANTLRException e) {
                throw new FormException(e.toString(),e,"timer_spec");
            }
        }
    }
}
