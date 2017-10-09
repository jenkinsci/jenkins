package jenkins.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.util.io.OnMaster;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * System properties provider, powered by {@link ServletContext}.
 * @author Oleg Nenashev
 * @see SystemProperties
 */
@Restricted(NoExternalUse.class)
public class ServletContextSystemPropertiesProvider extends SystemPropertiesProvider implements OnMaster, ServletContextListener {

    // this class implements ServletContextListener and is declared in WEB-INF/web.xml

    /**
     * The ServletContext to get the "init" parameters from.
     */
    @CheckForNull
    private static ServletContext theContext;

    /**
     * Called by the servlet container to initialize the {@link ServletContext}.
     */
    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "Currently Jenkins instance may have one ond only one context")
    public void contextInitialized(ServletContextEvent event) {
        theContext = event.getServletContext();
        SystemPropertiesProvider.addProvider(this);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // nothing to do
    }

    @CheckForNull
    @Override
    public String getProperty(@Nonnull String key) {
        return theContext != null ? theContext.getInitParameter(key) : null;
    }
}
