package jenkins.model.logging;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Locates logging methods for runs.
 * @author Oleg Nenashev
 * @since TODO
 */
public abstract class LoggingMethodLocator implements ExtensionPoint {
  
  /**
   * Retrieve the logging method for the run.
   * @param run Run
   * @return Logging method. {@code null} if the locator does not provide the 
   *         implementation for the run.
   */
  @CheckForNull
  protected abstract LoggingMethod getLoggingMethod(Run run);
  
  @Nonnull
  public static LoggingMethod locate(Run run) {
      for (LoggingMethodLocator locator : all()) {
          final LoggingMethod loggingMethod = locator.getLoggingMethod(run);
          if (loggingMethod != null) {
              return loggingMethod;
          }
      }
      // Fallback
      return run.getDefaultLoggingMethod();
  }
  
  public static ExtensionList<LoggingMethodLocator> all() {
      return ExtensionList.lookup(LoggingMethodLocator.class);
  }
}