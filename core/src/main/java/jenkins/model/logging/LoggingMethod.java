package jenkins.model.logging;

import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Run;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public abstract class LoggingMethod implements Serializable {
  
  //private final LoggingMethodLocator.JobData data;
  
  //public LoggingMethod(LoggingMethodLocator.JobData data) {
  //  this.data = data;
  //}
  
  @CheckForNull
  public abstract ConsoleLogFilter createLoggerDecorator(Run<?, ?> build);
  
  //public abstract OutputStreamWrapper getLogger();
  
 // public abstract void initializeLogger();
  
 // public abstract void finalizeLogger();
  
  //public abstract class OutputStreamWrapper {
  //  public abstract Object readResolve();
  //}
  
  public abstract Launcher decorateLauncher(@Nonnull Launcher l, @Nonnull Run run, @Nonnull Node node); 
  

  
  /** 
   * Fallback Logging methods for jobs, which do not define the implementation. 
   */
  public static final LoggingMethod NOOP = new NoopLoggingMethod();
  private static class NoopLoggingMethod extends LoggingMethod {
        @Override
        public Launcher decorateLauncher(Launcher l, Run run, Node node) {
            return l;
        }

        @Override
        public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
            return null;
        }
  } 
  
  /**
   * Default logging method for {@link AbstractProject} classes
   */
  public static class DefaultAbstractBuildLoggingMethod extends NoopLoggingMethod {
      // Default impl
  } 
}