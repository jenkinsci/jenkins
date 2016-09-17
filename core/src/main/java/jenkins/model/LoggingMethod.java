package jenkins.model;

import hudson.remoting.RemoteOutputStream;
import java.io.Serializable;

public abstract class LoggingMethod implements Serializable {
  
  private final LoggingMethod.JobData data;
  
  public LoggingMethod(LoggingMethod.JobData data) {
    this.data = data;
  }
  
  public abstract OutputStreamWrapper getLogger();
  
  public abstract void finalizeLogger();
  
  public abstract class OutputStreamWrapper {
    public abstract Object readResolve();
  }
}