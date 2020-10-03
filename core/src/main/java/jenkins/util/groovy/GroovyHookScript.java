package jenkins.util.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletContext;
import jenkins.model.Jenkins;

/**
 * A collection of Groovy scripts that are executed as various hooks.
 *
 * <p>
 * By default, for a given hook type, like "init", the following locations are searched for hook scripts,
 * and then they are executed in turn.
 *
 * <ol>
 * <li>/WEB-INF/<i>HOOK</i>.groovy in the war file
 * <li>/WEB-INF/<i>HOOK</i>.groovy.d/*.groovy in the war file
 * <li>$JENKINS_HOME/<i>HOOK</i>.groovy
 * <li>$JENKINS_HOME/<i>HOOK</i>.groovy.d/*.groovy
 * </ol>
 *
 * If a file path or a directory path is specified for the script look up,
 * then the following locations are searched for hook scripts, and then they are executed in turn.
 *
 * <ol>
 * <li>/WEB-INF/<i>HOOK</i>.groovy in the war file
 * <li>/WEB-INF/<i>HOOK</i>.groovy.d/*.groovy in the war file
 * <li>{@code hookGroovy}.groovy
 * <li>{@code hookGroovyD}/*.groovy
 * </ol>
 *
 * <p>
 * Scripts inside {@code /WEB-INF} is meant for OEM distributions of Jenkins. Files inside
 * {@code $JENKINS_HOME} are for installation local settings. Use of {@code HOOK.groovy.d}
 * allows configuration management tools to control scripts easily.
 *
 * @author Kohsuke Kawaguchi
 */
public class GroovyHookScript {
    private final String type;
    private final File hookGroovy;
    private final File hookGroovyD;
    private final Binding bindings = new Binding();
    private final ServletContext servletContext;
    private final ClassLoader loader;

    @Deprecated
    public GroovyHookScript(String hook) {
        this(hook, Jenkins.get());
    }

    private GroovyHookScript(String hook, Jenkins j) {
        this(hook, j.servletContext, j.getRootDir(), j.getPluginManager().uberClassLoader);
    }

    public GroovyHookScript(String hook, @NonNull ServletContext servletContext, @NonNull File home, @NonNull ClassLoader loader) {
        this(hook,
              defaultHookPath(hook, home, false),
              defaultHookPath(hook, home, true),
              servletContext,
              home,
              loader
        );
    }

    /**
     *
     * @param type the hook type (eg init for an init Groovy Script)
     * @param hookGroovy the path to a Groovy Script to execute
     *                   If null, then it falls back to the default hook path.
     * @see GroovyHookScript#defaultHookPath(String, File, boolean)
     * @param hookGroovyD the path to a directory of Groovy Scripts to execute
     *                    If null, then it falls back to the default hook path.
     * @see GroovyHookScript#defaultHookPath(String, File, boolean)
     * @param servletContext
     * @param home JENKINS_HOME
     * @param loader
     */
    public GroovyHookScript(String type,
                            File hookGroovy,
                            File hookGroovyD,
                            @NonNull ServletContext servletContext,
                            @NonNull File home,
                            @NonNull ClassLoader loader) {
        this.type = type;
        if (hookGroovy != null) {
            File canonicalPath;
            try {
                canonicalPath = hookGroovy.getCanonicalFile();
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to retrieve a canonical path, falling back to default hook path", e);
                canonicalPath  = defaultHookPath(type, home, false);
            }
            this.hookGroovy = canonicalPath;
        } else {
            this.hookGroovy = defaultHookPath(type, home, false);
        }

        if (hookGroovyD != null) {
            File canonicalPath;
            try {
                canonicalPath = hookGroovyD.getCanonicalFile();
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to retrieve a canonical path for " + hookGroovyD +", falling back to default hook directory path", e);
                canonicalPath = defaultHookPath(type, home, true);
            }
            this.hookGroovyD = canonicalPath;
        } else {
            this.hookGroovyD = defaultHookPath(type, home, true);
        }

        this.servletContext = servletContext;
        this.loader = loader;
    }

    private static File defaultHookPath(String type, @NonNull File home, boolean directory) {
        return new File(home, type + ".groovy" + (directory ? ".d" : ""));
    }

    public GroovyHookScript bind(String name, Object o) {
        bindings.setProperty(name,o);
        return this;
    }

    public Binding getBindings() {
        return bindings;
    }

    public void run() {
        try {
            URL bundled = servletContext.getResource("/WEB-INF/"+ type + ".groovy");
            execute(bundled);
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to execute /WEB-INF/" + type + ".groovy",e);
        }

        Set<String> resources = servletContext.getResourcePaths("/WEB-INF/" + type + ".groovy.d" +"/");
        if (resources!=null) {
            // sort to execute them in a deterministic order
            for (String res : new TreeSet<>(resources)) {
                try {
                    URL bundled = servletContext.getResource(res);
                    execute(bundled);
                } catch (IOException e) {
                    LOGGER.log(WARNING, "Failed to execute " + res, e);
                }
            }
        }

        execute(hookGroovy);

        if (hookGroovyD.isDirectory()) {
            File[] scripts = hookGroovyD.listFiles(f -> f.getName().endsWith(".groovy"));
            if (scripts!=null) {
                // sort to run them in a deterministic order
                Arrays.sort(scripts);
                for (File f : scripts) {
                    execute(f);
                }
            }
        }
    }

    protected void execute(URL bundled) throws IOException {
        if (bundled!=null) {
            LOGGER.info("Executing bundled script: "+bundled);
            execute(new GroovyCodeSource(bundled));
        }
    }

    protected void execute(File f) {
        if (f.exists()) {
            LOGGER.info("Executing "+f);
            try {
                execute(new GroovyCodeSource(f));
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to execute " + f, e);
            }
        }
    }

    protected void execute(GroovyCodeSource s) {
        try {
            createShell().evaluate(s);
        } catch (RuntimeException x) {
            LOGGER.log(WARNING, "Failed to run script " + s.getName(), x);
        }
    }

    /**
     * Can be used to customize the environment in which the script runs.
     */
    protected GroovyShell createShell() {
        return new GroovyShell(loader, bindings);
    }

    private static final Logger LOGGER = Logger.getLogger(GroovyHookScript.class.getName());
}
