package hudson.cli.declarative;

import hudson.Extension;
import hudson.ExtensionFinder;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.cli.CloneableCLICommand;
import hudson.model.Hudson;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.localizer.ResourceBundleHolder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;

/**
 * Discover {@link CLIMethod}s and register them as {@link CLICommand} implementations.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CLIRegisterer extends ExtensionFinder {
    public <T> Collection<T> findExtensions(Class<T> type, Hudson hudson) {
        if (type==CLICommand.class)
            return (List<T>)discover(hudson);
        else
            return Collections.emptyList();
    }

    private List<CLICommand> discover(final Hudson hudson) {
        LOGGER.fine("Listing up @CLIMethod");
        List<CLICommand> r = new ArrayList<CLICommand>();

        try {
            for ( final Method m : Util.filter(Index.list(CLIMethod.class, hudson.getPluginManager().uberClassLoader),Method.class)) {
                try {
                    // command name
                    final String name = m.getAnnotation(CLIMethod.class).name();

                    final ResourceBundleHolder res = loadMessageBundle(m);
                    res.format("CLI."+name+".shortDescription");   // make sure we have the resource, to fail early

                    r.add(new CloneableCLICommand() {
                        @Override
                        public String getName() {
                            return name;
                        }

                        public String getShortDescription() {
                            // format by using the right locale
                            return res.format(name+".shortDescription");
                        }

                        protected int run() throws Exception {
                            try {
                                m.invoke(resolve(m.getDeclaringClass()));
                                return 0;
                            } catch (InvocationTargetException e) {
                                Throwable t = e.getTargetException();
                                if (t instanceof Exception)
                                    throw (Exception) t;
                                throw e;
                            }
                        }

                        /**
                         * Finds an instance to invoke a CLI method on.
                         */
                        private Object resolve(Class type) {
                            if (Modifier.isStatic(m.getModifiers()))
                                return null;

                            // TODO: support resolver
                            return hudson;
                        }
                    });
                } catch (ClassNotFoundException e) {
                    LOGGER.log(SEVERE,"Failed to process @CLIMethod: "+m,e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to discvoer @CLIMethod",e);
        }

        return r;
    }

    /**
     * Locates the {@link ResourceBundleHolder} for this CLI method.
     */
    private ResourceBundleHolder loadMessageBundle(Method m) throws ClassNotFoundException {
        Class c = m.getDeclaringClass();
        Class<?> msg = c.getClassLoader().loadClass(c.getName().substring(0, c.getName().lastIndexOf(".")) + ".Messages");
        return ResourceBundleHolder.get(msg);
    }

    private static final Logger LOGGER = Logger.getLogger(CLIRegisterer.class.getName());
}
