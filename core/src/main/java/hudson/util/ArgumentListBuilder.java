package hudson.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Used to build up arguments for a process invocation.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArgumentListBuilder {
    private final List<String> args = new ArrayList<String>();

    public ArgumentListBuilder add(String a) {
        args.add(a);
        return this;
    }

    /**
     * Adds an argument by quoting it.
     * This is necessary only in a rare circumstance,
     * such as when adding argument for ssh and rsh.
     *
     * Normal process invcations don't need it, because each
     * argument is treated as its own string and never merged into one. 
     */
    public ArgumentListBuilder addQuoted(String a) {
        return add('"'+a+'"');
    }

    public ArgumentListBuilder add(String... args) {
        for (String arg : args) {
            add(arg);
        }
        return this;
    }

    /**
     * Decomposes the given token into multiple arguments by splitting via whitespace.
     */
    public ArgumentListBuilder addTokenized(String s) {
        StringTokenizer tokens = new StringTokenizer(s);
        while(tokens.hasMoreTokens())
            add(tokens.nextToken());
        return this;
    }

    public String[] toCommandArray() {
        return args.toArray(new String[args.size()]);
    }
}
