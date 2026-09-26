package jenkins.model;

import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;

/**
 * Convenient interface adding form validation for parameter 'name' fields, emitting a warning if it case-insensitively matches a known environment variable causing trouble during the build.
 * <p>
 *     This is intended to check for accidental name matches (in particular given Jenkins's case insensitive behavior), rather than intentional matches.
 *     Someone who wants to set LD_PRELOAD or DYLD_LIBRARY_PATH does so intentionally.
 * </p>
 *
 * @since TODO
 */
public interface EnvironmentVariableParameterNameFormValidation {
    default FormValidation doCheckName(@QueryParameter String value) {
        if (Configuration.DISCOURAGED_NAMES.contains(value)) {
            return FormValidation.warning(Messages.ParameterNameFormValidation_Warning(value));
        }
        return FormValidation.ok();
    }

    @Restricted(NoExternalUse.class)
    class Configuration {
        private static final Logger LOGGER = Logger.getLogger(EnvironmentVariableParameterNameFormValidation.class.getName());
        private static final String NAMES = SystemProperties.getString(EnvironmentVariableParameterNameFormValidation.class.getName() + ".NAMES", "HOME,PATH,USER");

        private static final Collection<String> DISCOURAGED_NAMES = new TreeSet<>(String::compareToIgnoreCase);

        static {
            final List<String> names = new ArrayList<>(Arrays.stream(NAMES.split(",")).map(String::trim).toList());
            LOGGER.log(Level.CONFIG, () -> "Setting environment variable names causing form validation warnings: " + names);
            DISCOURAGED_NAMES.addAll(names);
        }
    }
}
