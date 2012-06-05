package hudson.search;

import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import java.util.Set;
import java.util.TreeSet;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

public class UserSearchProperty extends hudson.model.UserProperty {
    
    private final boolean insensitiveSearch;
    
    private final boolean showAllPossibleResults; //show all possible result not only suggestion or founded item
    
    private final Set<String> filter; //Set of result types , which will be diesplayed

    public UserSearchProperty(boolean insensitiveSearch, boolean showAllPossibleResults, Set<String> filter) {
        this.insensitiveSearch = insensitiveSearch;
        this.showAllPossibleResults=showAllPossibleResults;
        this.filter=filter;
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
    
    public Set<String> getFilter(){
        return filter;
    }
    
    public boolean getShowAllPossibleResults(){
        return showAllPossibleResults;
    }
    
    public static boolean showAllPossibleResults(){
        User user = User.current();
        //Searching for anonymous user shows all result only if there is not any instance of SearchableModelObject whith 
        //the same search name as the searched query
        boolean allSuggestedResults = false;
        if(user!=null && user.getProperty(UserSearchProperty.class).getShowAllPossibleResults()){
          allSuggestedResults=true;
        }
        return allSuggestedResults;
    }
    
    @Extension
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        public String getDisplayName() {
            return "Setting for search";
        }

        public UserProperty newInstance(User user) {
            return new UserSearchProperty(false, false, null); //default setting is case-sensitive searching
        }

        @Override
        public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            boolean showAllPossibleResults = req.getParameter("showAllPossibleResults") !=null;
            if(showAllPossibleResults){
                Set<String> filter = new TreeSet<String>();
                String values[] = {"other","job","build","view","computer","user"};
                for(String s:values){
                   if(req.getParameter(s)!=null)
                      filter.add(s); 
                }
                return new UserSearchProperty(formData.optBoolean("insensitiveSearch"), showAllPossibleResults,filter);
            }
            return new UserSearchProperty(formData.optBoolean("insensitiveSearch"),false, null);
        }

    }

}