package hudson.search;

import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.search.Search.TokenList;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Search all possible results
 * It is different from Search class - it display all searched results, not only suggestions or found item. 
 * The results are categorized by five types and can be filtered by this type.
 * 
 * @author Lucie Votypkova
 */
public class SearchAllResults extends Search{

    //class which contains informations about searched object
    public class SearchedItem implements Comparable {
        Ancestor ancestor;
        Object object;
        SearchItem item;
        Type type;
        

        public SearchedItem(SearchItem item, Ancestor ancestor) {
            this.ancestor=ancestor;
            this.item=item;
        }

        public int compareTo(Object o) {
            if (o instanceof SearchedItem) {
                SearchedItem i = (SearchedItem) o;
                if (!getName().equals(i.getName())) {
                    return getName().compareTo(i.getName());
                } else {
                    return getUrl().compareTo(i.getUrl());
                }
            }
            throw new IllegalArgumentException("Instance of class" + SearchedItem.class + " must be compared only with instance of the same class");
        }

        public String getName() {
            return item.getSearchName();
        }

        public String getUrl() {
            String ancestorUrl = ancestor.getUrl();
            if ((!ancestorUrl.endsWith("/")) && (!item.getSearchUrl().startsWith("/"))) {
                ancestorUrl = ancestorUrl + "/";
            }
            return ancestorUrl + item.getSearchUrl();
        }
        
        public void setObject(Object object){
            this.object=object;
        }
        
        public void setType(Type type){
            this.type=type;
        }    

        public String getDescription() {
            return type.getDescription();
        }        
        
        public String getIconUrl(){
            return type.getIconUrl(object);
        }
        
    }
    
    /**
     * Set filter for searching
     * 
     * @return Set with included types of results
     */
    protected Set<String> getFilter(StaplerRequest req) throws ServletException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        Set<String> filter = new TreeSet<String>();
        String values[] = {"other","job","view","computer","user"};
        for(String s:values){
            if(req.getParameter(s)!=null)
                filter.add(s); 
        }
        return filter;
    }

    /**
     * Return all searched items. This items are categorized into five 
     * types (job, user, computer, view and other)
     * 
     * @return List with results
     */
    public List<SearchedItem> getAllSearchedItems(StaplerRequest req, String tokenList) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, ServletException {
        Set<String> filteredItems= new TreeSet<String>();
        if("Filter".equals(req.getParameter("submit"))){ //user wanto to change setting for this search
            filteredItems = getFilter(req);
        }
        else{
            filteredItems = User.current().getProperty(UserSearchProperty.class).getFilter();
        }
        req.setAttribute("filteredItems", filteredItems);
        return getResolvedAndFilteredItems(tokenList, req, filteredItems);
    }
    
    /**
     * Return ser with items, but without categorization (type of searched item is not resolved)
     * 
     * @return Set with items
     */
    public Set<SearchedItem> getAllItems(StaplerRequest req, String tokenList){
        List<Ancestor> ancestors = req.getAncestors();        
        Set<SearchedItem> searchedItems = new TreeSet<SearchedItem>();
        for (int i = 0; i < ancestors.size(); i++) {
            Ancestor a = ancestors.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                SearchIndex index = ((SearchableModelObject) a.getObject()).getSearchIndex();
                TokenList tokens = new TokenList(tokenList);
                if (tokens.length() == 0) {
                    return searchedItems;
                }
                List<SearchItem> items = new ArrayList<SearchItem>();
                for (String token : tokens.subSequence(0)) {
                    index.suggest(token, items);  
                    searchedItems.addAll(createSearchItems(items, a));
                }
            }
        }
        return searchedItems;
    }
        
    /**
     * Create set of {@link SearchedItem} from list of {@link SearchItem}
     * 
     * @return Set of {@link SearchedItem}
     */
    private Set<SearchedItem> createSearchItems(List<SearchItem> items, Ancestor ancestor){
        Set<SearchedItem> searchedItems= new TreeSet<SearchedItem>();
        for(SearchItem si:items){
            SearchedItem item = new SearchedItem(si, ancestor);
            searchedItems.add(item);
        }
        return searchedItems;
    }
    
    /**
     * Resolve types of searched items
     * 
     * @return List of {@link SearchedItem}
     */
    protected List<SearchedItem> getResolvedAndFilteredItems(String tokens, StaplerRequest req, Set<String> filteredItems) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
       Set<SearchedItem>  items = getAllItems(req,tokens);
        List<SearchedItem> resolvedAndFileteredItems = new ArrayList<SearchedItem>();
        for(SearchedItem si:items){
            if(resolveType(si,si.ancestor) && filteredItems.contains(si.type.getDescription())){
                resolvedAndFileteredItems.add(si);
            }
        }
        return resolvedAndFileteredItems;
    }
    
    @Override
     public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this, "all-results.jelly").forward(req, rsp);
     }
    
    private boolean _resolveType(Jenkins jenkins, SearchedItem item) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        String[] split = item.getUrl().split("/");
        if(split.length==0){ //main view
            item.setType(Type.VIEW);
            return true;
        }
        if(split.length==3){
            String type = split[1];
            if(type.equals("job")){
                item.setType(Type.JOB);
                TopLevelItem job = jenkins.getItem(split[2]);
                item.setObject(job);
                return job!=null;
            }
            if(type.equals("user")){
                User user = jenkins.getUser(split[2]);
                item.setType(Type.USER);
                item.setObject(user);
                return user!=null;
            }
            if(type.equals("computer")){
                item.setType(Type.COMPUTER);
                Computer computer = jenkins.getComputer(split[2]);
                item.setObject(computer);
                return computer!=null;
            }
            if(type.equals("view")){
                item.setType(Type.VIEW);
                return jenkins.getView(split[2])!=null;
            }     
        }
        item.setType(Type.OTHER);
        return true;
    }

    /**
     * Resolve which object is assigned to given URL. (It assigns the appropriate type to searched item)
     * 
     * @param SearchedItem item, Ancestor ancestor
     * 
     * @return return true if the resolving was successful and the searched item should be included, otherwise false;
     */
    private boolean resolveType(SearchedItem item,Ancestor ancestor) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Object o = ancestor.getObject();
        if (o instanceof Jenkins){
            return _resolveType((Jenkins)o, item);
        }
        item.setType(Type.OTHER);
        return true;
    }

    
    public enum Type {

        JOB {
            String getIconUrl(Object o){
                if (((Job)o).getBuilds().size() == 0) {
                    return Functions.getResourcePath() + "/images/16x16/health-80plus.png";
                } else {
                    return Functions.getResourcePath() + "/images/16x16/" + ((Job)o).getBuildHealth().getIconUrl();
                }
            }
            String getDescription(){
                return "job";
            }
        },
        COMPUTER {
            String getIconUrl(Object o){
                if(o instanceof MasterComputer){
                    return Functions.getResourcePath() + "/images/16x16/computer.png"; 
                }
                return Functions.getResourcePath() + "/images/16x16/" + ((Computer)o).getIcon();
            }
            String getDescription(){
                return "computer";
            }
        },
        VIEW {
            String getIconUrl(Object o){
                return Functions.getResourcePath() + "/images/16x16/folder.png";
            }
            String getDescription(){
                return "view";
            }
        },
        USER{
            String getIconUrl(Object o){
                return Functions.getAvatar((User)o, "16x16");
            }
            String getDescription(){
                return "user";
            }
        },
        OTHER {
            String getIconUrl(Object o){
                return Functions.getResourcePath() + "/images/16x16/notepad.png";
            }
            String getDescription(){
                return "other";
            }
        };
        
        abstract String getDescription();
        
        abstract String getIconUrl(Object o);
    }

}

