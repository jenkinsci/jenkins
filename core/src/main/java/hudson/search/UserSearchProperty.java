package hudson.search;

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import net.sf.json.JSONObject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

public class UserSearchProperty extends hudson.model.UserProperty {
    
    private final boolean insensitiveSearch;

    public UserSearchProperty(boolean insensitiveSearch) {
        this.insensitiveSearch = insensitiveSearch;
    }

    @Exported
    public boolean getInsensitiveSearch() {
        return insensitiveSearch;
    }
    
    public static boolean isCaseInsensitive(){
        User user = User.current();
        boolean caseInsensitive = false;
        if(user!=null && user.getProperty(UserSearchProperty.class).getInsensitiveSearch()){//Searching for anonymous user is case-sensitive
          caseInsensitive=true;
        }
        return caseInsensitive;
    }
    

    @Extension @Symbol("search")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public String getDisplayName() {
            return Messages.UserSearchProperty_DisplayName();
        }

        public UserProperty newInstance(User user) {
            return new UserSearchProperty(false); //default setting is case-sensitive searching
        }

        @Override
        public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new UserSearchProperty(formData.optBoolean("insensitiveSearch"));
        }

    }

}