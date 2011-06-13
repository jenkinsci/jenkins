package hudson.restapi.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import hudson.model.Run;

public class Build {
    private String jobName;
    private int buildNumber;
    private long duration;
    private long estimatedDuration;
    private Date startTime;
    private boolean isBuilding;
    private String status;
    private List<Artifact> artifacts;
    
    public Build() { }
    
    @SuppressWarnings("rawtypes")
    public Build(Run run) {
        this(run.getParent().getName(), 
             run.getNumber(), 
             run.getDuration(), 
             pullArtifacts(run), 
             run.getEstimatedDuration(), 
             run.getTime(), 
             run.isBuilding(),
             run.getResult().toString());
    }
    
    private static List<Artifact> pullArtifacts(@SuppressWarnings("rawtypes") Run run) {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        if (run.getHasArtifacts()) {
            for (Object obj : run.getArtifacts()) {
                @SuppressWarnings("rawtypes")
                hudson.model.Run.Artifact artifact = (hudson.model.Run.Artifact)obj;
                artifacts.add(new Artifact(artifact));
            }
        }
        return artifacts;
    }
    
    public Build(String jobName, int buildNumber, long duration, List<Artifact> artifacts, long estimatedDuration, Date startTime, boolean isBuilding, String status) {
        this.jobName = jobName;
        this.buildNumber = buildNumber;
        this.duration = duration;
        this.estimatedDuration = estimatedDuration;
        this.startTime = startTime;
        this.isBuilding = isBuilding;
        this.artifacts = artifacts;
        this.status = status;
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

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
