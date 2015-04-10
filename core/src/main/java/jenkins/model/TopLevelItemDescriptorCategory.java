package jenkins.model;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class TopLevelItemDescriptorCategory {
    private final String id;
    private final Localizable displayName;
    private final double ordinal;

    public TopLevelItemDescriptorCategory(double ordinal, Localizable displayName) {
        this.id = displayName.getKey();
        this.displayName = displayName;
        this.ordinal = ordinal;
    }

    @Exported
    public String getId() {
        return id;
    }
    
    @Exported
    public String getDisplayName() {
        return displayName.toString();
    }

    @Exported
    public double getOrdinal() {
        return ordinal;
    }

    public static final TopLevelItemDescriptorCategory JOBS_AND_WORKFLOWS = 
            new TopLevelItemDescriptorCategory(0, Messages._Category_JobAndWorkflows());
    public static final TopLevelItemDescriptorCategory ORGANIZATION_AND_VISUALIZATION =
            new TopLevelItemDescriptorCategory(100, Messages._Category_OrganizationAndVisualization());
    public static final TopLevelItemDescriptorCategory OTHERS =
            new TopLevelItemDescriptorCategory(100000/*very big number*/, Messages._Category_Others());
}
