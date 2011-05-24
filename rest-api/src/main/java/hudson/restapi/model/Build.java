package hudson.restapi.model;

import java.util.Date;
import hudson.model.Run;

public class Build {
    private String jobName;
    private int buildNumber;
    private long duration;
    private boolean hasArtifacts;
    private long estimatedDuration;
    private Date startTime;
    private boolean isBuilding;
    
    public Build() { }
    
    @SuppressWarnings("rawtypes")
    public Build(Run run) {
        this(run.getParent().getName(), run.getNumber(), run.getDuration(), run.getHasArtifacts(), run.getEstimatedDuration(), run.getTime(), run.isBuilding());
    }
    
    public Build(String jobName, int buildNumber, long duration, boolean hasArtifacts, long estimatedDuration, Date startTime, boolean isBuilding) {
        this.jobName = jobName;
        this.buildNumber = buildNumber;
        this.duration = duration;
        this.hasArtifacts = hasArtifacts;
        this.estimatedDuration = estimatedDuration;
        this.startTime = startTime;
        this.isBuilding = isBuilding;
    }

    public String getJobName() {
        return this.jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public void setHasArtifacts(boolean hasArtifacts) {
        this.hasArtifacts = hasArtifacts;
    }

    public boolean getHasArtifacts() {
        return hasArtifacts;
    }

    public void setEstimatedDuration(long estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public long getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getStartTime() {
        return this.startTime;
    }

    public void setBuilding(boolean isBuilding) {
        this.isBuilding = isBuilding;
    }

    public boolean isBuilding() {
        return this.isBuilding;
    }
}
