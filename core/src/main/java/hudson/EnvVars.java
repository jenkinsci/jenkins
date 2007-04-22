package hudson;

import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class EnvVars {
    /**
     * Environmental variables that we've inherited.
     */
    public static final Map<String,String> masterEnvVars = System.getenv();
}
