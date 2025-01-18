package hudson.search;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;

public class UserSearchProperty extends hudson.model.UserProperty {

    private static final boolean DEFAULT_SEARCH_CASE_INSENSITIVE_MODE = true;

    private final boolean insensitiveSearch;

    public UserSearchProperty(boolean insensitiveSearch) {
        this.insensitiveSearch = insensitiveSearch;
    }

    @Exported
    public boolean getInsensitiveSearch() {
        return insensitiveSearch;
    }

    public static boolean isCaseInsensitive() {
        User user = User.current();

        if (user == null) {
            return DEFAULT_SEARCH_CASE_INSENSITIVE_MODE;
        }

        return user.getProperty(UserSearchProperty.class).getInsensitiveSearch();
    }


    @Extension @Symbol("search")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.UserSearchProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new UserSearchProperty(DEFAULT_SEARCH_CASE_INSENSITIVE_MODE);
        }

        @Override
        public UserProperty newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            return new UserSearchProperty(formData.optBoolean("insensitiveSearch"));
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }
    }

}
