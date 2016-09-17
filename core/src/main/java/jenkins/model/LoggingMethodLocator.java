package jenkins.model;

import hudson.ExtensionPoint;
import java.io.Serializable;

public abstract class LoggingMethodLocator implements ExtensionPoint {
  
  /** Provide Logging Method for given job. */
  public LoggingMethod getLoggingMethod(Job job) {
    JobData data = constructJobData(job);
    return getLoggingMethod(data);
  }
  
  private abstract JobData constructJobData(Job job);
  
  private abstract getLoggingMethod(JobData data);
  
  public class JobData implements Serializable {}
}