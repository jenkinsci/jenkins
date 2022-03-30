
package hudson.views;

import hudson.Extension;
import hudson.model.Item;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Renders {@link Item#getName()}
 */
public class LastDescriptionColumn extends ListViewColumn {
    @DataBoundConstructor
    public LastDescriptionColumn() {
    }

    // put this in the middle of icons and properties
    @Extension(ordinal = DEFAULT_COLUMNS_ORDINAL_PROPERTIES_START - 5) @Symbol("lastDescription")
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.LastDescriptionColumn_DisplayName();
        }
    }

}
