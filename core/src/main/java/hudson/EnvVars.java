package hudson;

import org.apache.tools.ant.taskdefs.Execute;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * @author Kohsuke Kawaguchi
 */
public class EnvVars {
    /**
     * Environmental variables that we've inherited.
     */
    public static final Map<String,String> masterEnvVars;

    static {
        Vector<String> envs = Execute.getProcEnvironment();
        Map<String,String> m = new HashMap<String,String>();
        for (String e : envs) {
            int idx = e.indexOf('=');
            m.put(e.substring(0, idx), e.substring(idx + 1));
        }
        masterEnvVars = Collections.unmodifiableMap(m);
    }

}
