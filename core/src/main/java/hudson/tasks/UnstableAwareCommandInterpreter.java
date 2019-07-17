package hudson.tasks;

import hudson.Util;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;

public abstract class UnstableAwareCommandInterpreter extends CommandInterpreter {
    public UnstableAwareCommandInterpreter(String command) {
        super(command);
    }

    interface InvalidExitCodeHelper {
        String messageZero();

        String messageRange(Object unstableReturn);

        int min();

        int max();
    }

    /**
     * Performs on-the-fly validation of the exit code.
     */
    protected static FormValidation helpCheckUnstableReturn(@QueryParameter String value, InvalidExitCodeHelper helper) {
        value = Util.fixEmptyAndTrim(value);
        if (value == null) {
            return FormValidation.ok();
        }
        long unstableReturn;
        try {
            unstableReturn = Long.parseLong(value);
        } catch (NumberFormatException e) {
            return FormValidation.error(hudson.model.Messages.Hudson_NotANumber());
        }
        if (unstableReturn == 0) {
            return FormValidation.warning(helper.messageZero());
        }
        if (unstableReturn < helper.min() || unstableReturn > helper.max()) {
            return FormValidation.error(helper.messageRange(unstableReturn));
        }
        return FormValidation.ok();
    }

}
