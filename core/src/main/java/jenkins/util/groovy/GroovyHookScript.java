package jenkins.util.groovy;

import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A collection of Groovy scripts that are executed as various hooks.
 *
 * <p>
 * For a given hook name, like "init", the following locations are searched for hook scripts,
 * and then they are executed in turn.
 *
 * <ol>
 * <li>/WEB-INF/<i>HOOK</i>.groovy in the war file
 * <li>/WEB-INF/<i>HOOK</i>.groovy.d/*.groovy in the war file
 * <li>$JENKINS_HOME/<i>HOOK</i>.groovy
 * <il>$JENKINS_HOME/<i>HOOK</i>.groovy.d/*.groovy
 * </ol>
 *
 * <p>
 * Scripts inside <tt>/WEB-INF</tt> is meant for OEM distributions of Jenkins. Files inside
 * <tt>$JENKINS_HOME</tt> are for installation local settings. Use of <tt>HOOK.groovy.d</tt>
 * allows configuration management tools to control scripts easily.
 *
 * @author Kohsuke Kawaguchi
 */
public class GroovyHookScript {
    private final String hook;

    public GroovyHookScript(String hook) {
        this.hook = hook;
    }

    public void run() throws IOException {
        Jenkins j = Jenkins.getInstance();
        final String hookGroovy = hook+".groovy";
        final String hookGroovyD = hook+".groovy.d";

        URL bundled = j.servletContext.getResource("/WEB-INF/"+ hookGroovy);
        execute(bundled);

        Set<String> resources = j.servletContext.getResourcePaths("/WEB-INF/"+ hookGroovyD +"/");
        if (resources!=null) {
            // sort to execute them in a deterministic order
            for (String res : new TreeSet<String>(resources)) {
                bundled = j.servletContext.getResource(res);
                execute(bundled);
            }
        }

        File script = new File(j.getRootDir(), hookGroovy);
        execute(script);

        File scriptD = new File(j.getRootDir(), hookGroovyD);
        if (scriptD.isDirectory()) {
            File[] scripts = scriptD.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    return f.getName().endsWith(".groovy");
                }
            });
            if (scripts!=null) {
                // sort to run them in a deterministic order
                Arrays.sort(scripts);
                for (File f : scripts)
                    execute(f);
            }
        }
    }

    protected void execute(URL bundled) throws IOException {
        if (bundled!=null) {
            LOGGER.info("Executing bundled script: "+bundled);
            execute(new GroovyCodeSource(bundled));
        }
    }

    protected void execute(File f) throws IOException {
        if (f.exists()) {
            LOGGER.info("Executing "+f);
            execute(new GroovyCodeSource(f));
        }
    }

    protected void execute(GroovyCodeSource s) {
        try {
            createShell().evaluate(s);
        } catch (RuntimeException x) {
            LOGGER.log(Level.WARNING, "Failed to run script " + s.getName(), x);
        }
    }

    /**
     * Can be used to customize the environment in which the script runs.
     */
    protected GroovyShell createShell() {
        return new GroovyShell(Jenkins.getInstance().getPluginManager().uberClassLoader);
    }

    private static final Logger LOGGER = Logger.getLogger(GroovyHookScript.class.getName());
}
