package jenkins.bootstrap;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.ServiceLoader;

/**
 * Interface for code that boots up Jenkins.
 *
 * <p>
 * Bootstrap code uses {@link ServiceLoader} to look for subtypes of this marker interface
 * and invokes {@link #contextInitialized(ServletContextEvent)} and {@link #contextDestroyed(ServletContextEvent)}
 *
 * @author Kohsuke Kawaguchi
 */
public interface BootLogic extends ServletContextListener {
    /**
     * Higher the number, the earlier it gets to run
     */
    float ordinal();
}
