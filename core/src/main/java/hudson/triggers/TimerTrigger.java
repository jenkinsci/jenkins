package hudson.triggers;

import static hudson.Util.fixNull;
import hudson.model.BuildableItem;
import hudson.model.Item;
import hudson.scheduler.CronTabList;
import hudson.util.FormFieldValidator;

import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import antlr.ANTLRException;

/**
 * {@link Trigger} that runs a job periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimerTrigger extends Trigger<BuildableItem> {
	
	@DataBoundConstructor
	public TimerTrigger(String timer_spec) throws ANTLRException {
        super(timer_spec);
    }

    public void run() {
        job.scheduleBuild();
    }

    public TriggerDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final TriggerDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends TriggerDescriptor {
        public DescriptorImpl() {
            super(TimerTrigger.class);
        }

        public boolean isApplicable(Item item) {
            return item instanceof BuildableItem;
        }

        public String getDisplayName() {
            return Messages.TimerTrigger_DisplayName();
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
                        String msg = CronTabList.create(fixNull(request.getParameter("value"))).checkSanity();
                        if(msg!=null)
                            warning(msg);
                        else
                            ok();
                    } catch (ANTLRException e) {
                        error(e.getMessage());
                    }
                }
            }.process();
        }
    }
}
