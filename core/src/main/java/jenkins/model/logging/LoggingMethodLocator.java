package jenkins.model.logging;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Job;
import hudson.model.Run;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public abstract class LoggingMethodLocator implements ExtensionPoint {
  
//  protected abstract JobData constructJobData(Run job);
  
  @CheckForNull
  protected abstract LoggingMethod getLoggingMethod(Run run);
  
  public class JobData implements Serializable {}
  
  public static ExtensionList<LoggingMethodLocator> all() {
      return ExtensionList.lookup(LoggingMethodLocator.class);
  }
  
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
  
}