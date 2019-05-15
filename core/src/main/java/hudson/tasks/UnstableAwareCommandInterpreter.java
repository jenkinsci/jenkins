package hudson.tasks;

import hudson.Util;
import hudson.util.FormValidation;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.QueryParameter;

public abstract class UnstableAwareCommandInterpreter extends CommandInterpreter {
    public UnstableAwareCommandInterpreter(String command) {
        super(command);
    }

    static String invalidExitCodeZero() { return null; };
    static String invalidExitCodeRange(Object o) { return null; };
    /**
     * Performs on-the-fly validation of the exit code.
     */
    protected static FormValidation helpCheckUnstableReturn(@QueryParameter String value) {
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
            return FormValidation.warning(invalidExitCodeZero());
        }
        if (unstableReturn < 1 || unstableReturn > 255) {
            return FormValidation.error(invalidExitCodeRange(unstableReturn));
        }
        return FormValidation.ok();
    }

}
