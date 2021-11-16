package jenkins.views;

import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.VersionNumber;

/**
 * {@link Header} that relies on core resources (images, CSS, JS, etc.) to perform
 * partial replacements.
 * 
 * Given this kind of header is not independent, compatibility should be managed by the
 * specific {@link Header#compatibilityHeaderVersion} value
 * 
 * @see Header
 */
public abstract class PartialHeader extends Header {

    private static Logger LOGGER = Logger.getLogger(PartialHeader.class.getName());
    
    /** When an incompatible change is made in the header (like the search form API), compatibility header version should be increased */
    private static final VersionNumber compatibilityHeaderVersion = new VersionNumber("1.0");
    
    @Override
    public boolean isCompatible() {
        return compatibilityHeaderVersion.isOlderThanOrEqualTo(getVersion());
    }
    
    /**
     * @return the header version
     */
    public abstract VersionNumber getVersion();
    
    @Initializer(after = InitMilestone.JOB_LOADED, before = InitMilestone.JOB_CONFIG_ADAPTED)
    public static void incompatibleHeaders() {
        for (PartialHeader header: ExtensionList.lookup(PartialHeader.class).stream().filter(h -> !h.isCompatible()).collect(Collectors.toList())) {
            LOGGER.warn(String.format("%s:%s not compatible with %s", header.getClass().getName(), header.getVersion(), compatibilityHeaderVersion));
        }
    }
}