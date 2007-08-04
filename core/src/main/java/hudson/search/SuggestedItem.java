package hudson.search;

/**
 * @author Kohsuke Kawaguchi
 */
public class SuggestedItem {
    private final SuggestedItem parent;
    public final SearchItem item;

    public SuggestedItem(SearchItem top) {
        this(null,top);
    }

    public SuggestedItem(SuggestedItem parent, SearchItem item) {
        this.parent = parent;
        this.item = item;
    }

    public String getPath() {
        if(parent==null)
            return item.getSearchName();
        else {
            StringBuilder buf = new StringBuilder();
            getPath(buf);
            return buf.toString();
        }
    }

    private void getPath(StringBuilder buf) {
        if(parent==null)
            buf.append(item.getSearchName());
        else {
            parent.getPath(buf);
            buf.append(' ').append(item.getSearchName());
        }
    }

    public String getUrl() {
        StringBuilder buf = new StringBuilder("$contextRoot");
        getUrl(buf);
        return buf.toString();
    }

    private void getUrl(StringBuilder buf) {
        if(parent!=null) {
            parent.getUrl(buf);
        }
        String f = item.getSearchUrl();
        if(f.startsWith("/")) {
            buf.setLength(0);
            buf.append(f);
        } else {
            if(buf.charAt(buf.length()-1)!='/')
                buf.append('/');
            buf.append(f);
        }
    }
}
