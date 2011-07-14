package jenkins.plugins.ui_samples;

import hudson.Extension;
import hudson.util.ComboBoxModel;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

/**
 * Combo box that changes the contents based on the values of other controls.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DynamicComboBox extends UISample {

    @Override
    public String getDescription() {
        return "Updates the contents of a combo box control dynamically based on selections of other controls";
    }

    // these getter methods should return the current value, which form the initial selection.

    public String getAlbum() {
        return "3";
    }

    public String getTitle() {
        return "Rocker";
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
        /**
         * This method determines the values of the album drop-down list box.
         */
        public ListBoxModel doFillAlbumItems() {
            ListBoxModel m = new ListBoxModel();
            m.add("Yellow Submarine","1");
            m.add("Abbey Road","2");
            m.add("Let It Be","3");
            return m;
        }

        /**
         * This method determines the values of the song title combo box.
         * Note that it takes the album information as a parameter, so the contents
         * of the combo box changes depending on the currently selected album.
         */
        public ComboBoxModel doFillTitleItems(@QueryParameter int album) {
            switch (album) {
            case 1:
                return new ComboBoxModel("Yellow Submarine","Only a Northern Song","All You Need Is Love");
            case 2:
                return new ComboBoxModel("Come Together","Something","I Want You");
            case 3:
                return new ComboBoxModel("The One After 909","Rocker","Get Back");
            default:
                // if no value is selected in the album, we'll get 0
                return new ComboBoxModel();
            }
        }

    }
}