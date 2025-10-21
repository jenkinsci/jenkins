package jenkins.views;

import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.AdministrativeError;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * {@link Header} that relies on core resources (images, CSS, JS, etc.) to perform
 * partial replacements.
 *
 * Given this kind of header is not independent, compatibility should be managed by the
 * specific {@link Header} compatibility header version value
 *
 * @see Header
 */
public abstract class PartialHeader extends Header {

    private static Logger LOGGER = Logger.getLogger(PartialHeader.class.getName());

    /**
     * The current compatibility version of the Header API.
     *
     * Increment this number when an incompatible change is made to the header (like the search form API).
     */
    private static final int compatibilityHeaderVersion = 2;

    @Override
    public final boolean isCompatible() {
        return compatibilityHeaderVersion == getSupportedHeaderVersion();
    }

    /**
     * @return the supported header version
     */
    public abstract int getSupportedHeaderVersion();

    @Initializer(after = InitMilestone.JOB_LOADED, before = InitMilestone.JOB_CONFIG_ADAPTED)
    @SuppressWarnings("unused")
    public static void incompatibleHeaders() {
        ExtensionList.lookup(PartialHeader.class).stream().filter(h -> !h.isCompatible()).forEach(header -> {
            LOGGER.warning(String.format(
                    "%s:%s not compatible with %s",
                    header.getClass().getName(),
                    header.getSupportedHeaderVersion(),
                    compatibilityHeaderVersion));
            new AdministrativeError(
                    header.getClass().getName(),
                    "Incompatible Header",
                    String.format("The plugin %s is attempting to replace the Jenkins header but is not compatible with this version of Jenkins. The plugin should be updated or removed.",
                            Jenkins.get().getPluginManager().whichPlugin(header.getClass())),
                    null);
        });
    }
}
