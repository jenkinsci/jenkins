package hudson.scm.browsers;

/**
 * @author Kohsuke Kawaguchi
*/
final class QueryBuilder {
    private final StringBuilder buf = new StringBuilder();

    QueryBuilder(String s) {
        add(s);
    }

    public QueryBuilder add(String s) {
        if(s==null)     return this; // nothing to add
        if(buf.length()==0) buf.append('?');
        else                buf.append('&');
        buf.append(s);
        return this;
    }

    public String toString() {
        return buf.toString();
    }
}
