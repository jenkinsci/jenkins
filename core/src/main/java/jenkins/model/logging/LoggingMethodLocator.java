package jenkins.model.logging;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Locates logging methods for runs.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class LoggingMethodLocator implements ExtensionPoint {
  
  /**
   * Retrieve the logging method for the run.
   * @param object Loggable object
   * @return Logging method. {@code null} if the locator does not provide the
   *         implementation for the run.
   */
  @CheckForNull
  protected abstract LoggingMethod getLoggingMethod(Loggable object);

    /**
     * Retrieve the log browser which should be used for a run.
     * @param object Loggable object
     * @return Logging method. {@code null} if the locator does not provide the
     *         implementation for the run.
     */
    @CheckForNull
    protected abstract LogBrowser getLogBrowser(Loggable object);

  @Nonnull
  public static LoggingMethod locate(Loggable run) {
      for (LoggingMethodLocator locator : all()) {
          final LoggingMethod loggingMethod = locator.getLoggingMethod(run);
          if (loggingMethod != null) {
              return loggingMethod;
          }
      }
      // Fallback
      return run.getDefaultLoggingMethod();
  }

    @Nonnull
    public static LogBrowser locateBrowser(Loggable run) {
        for (LoggingMethodLocator locator : all()) {
            final LogBrowser browser = locator.getLogBrowser(run);
            if (browser != null) {
                return browser;
            }
        }

        // Fallback
        LogBrowser defaultFromLogStorage = locate(run).getDefaultLogBrowser();
        return defaultFromLogStorage != null
                ? defaultFromLogStorage
                : run.getDefaultLogBrowser();
    }
  
  public static ExtensionList<LoggingMethodLocator> all() {
      return ExtensionList.lookup(LoggingMethodLocator.class);
  }
}