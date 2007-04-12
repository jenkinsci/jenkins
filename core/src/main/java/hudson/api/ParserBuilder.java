package hudson.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and maintains {@link Parser}s, that are used to write out
 * the value representation of {@link ExposedBean exposed beans}.
 * @author Kohsuke Kawaguchi
 */
public class ParserBuilder {
    /**
     * Instanciated {@link Parser}s.
     * Registration happens in {@link Parser#Parser(ParserBuilder,Class)} so that cyclic references
     * are handled correctly.
     */
    /*package*/ final Map<Class,Parser> parsers = new ConcurrentHashMap<Class,Parser>();

    public <T> Parser<T> get(Class<T> type) {
        Parser p = parsers.get(type);
        if(p==null) {
            synchronized(this) {
                p = parsers.get(type);
                if(p==null)
                    p = new Parser<T>(this,type);
            }
        }
        return p;
    }
}
