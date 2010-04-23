package hudson.plugins.ui_samples;

import hudson.Extension;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

import static java.util.Arrays.asList;

/**
 * Example of a dynamic drop-down list box that changes the contents dynamically based on the values of other controls. 
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DynamicDropDownListBox extends UISample {

    @Override
    public String getDescription() {
        return "Updates the contents of a &lt;select> control dynamically based on selections of other controls";
    }

    // these getter methods should return the current value, which form the initial selection.

    public String getCountry() {
        return "USA";
    }

    public String getState() {
        return "USA:B";
    }

    public String getCity() {
        return "USA:B:Z";
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
        public ListBoxModel doFillStateItems(@QueryParameter String country) {
            ListBoxModel m = new ListBoxModel();
            for (String s : asList("A","B","C"))
                m.add(String.format("State %s in %s", s, country),
                        country+':'+s);
            return m;
        }

        public ListBoxModel doFillCityItems(@QueryParameter String country, @QueryParameter String state) {
            ListBoxModel m = new ListBoxModel();
            for (String s : asList("X","Y","Z"))
                m.add(String.format("City %s in %s %s", s, state, country),
                        state+':'+s);
            return m;
        }
    }
}
