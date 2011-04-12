package hudson.restapi.model;

import java.util.Date;
import hudson.model.Run;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "build")
public class Build {
    private String jobName;
    private int buildNumber;
    private long duration;
    private boolean hasArtifacts;
    private long estimatedDuration;
    private Date time;
    private boolean building;
    
    public Build() { }
    
    @SuppressWarnings("rawtypes")
    public Build(Run run) {
        setJobName(run.getParent().getName());
        setBuildNumber(run.getNumber());
        setDuration(run.getDuration());
        setHasArtifacts(run.getHasArtifacts());
        setEstimatedDuration(run.getEstimatedDuration());
        setTime(run.getTime());
        setBuilding(run.isBuilding());
    }

    @XmlElement
    public String getJobName() {
        return this.jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    @XmlElement
    public int getBuildNumber() {
        return buildNumber;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @XmlElement
    public long getDuration() {
        return duration;
    }

    public void setHasArtifacts(boolean hasArtifacts) {
        this.hasArtifacts = hasArtifacts;
    }

    @XmlElement
    public boolean getHasArtifacts() {
        return hasArtifacts;
    }

    public void setEstimatedDuration(long estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    @XmlElement
    public long getEstimatedDuration() {
        return estimatedDuration;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    @XmlElement
    public Date getTime() {
        return time;
    }

    public void setBuilding(boolean building) {
        this.building = building;
    }

    @XmlElement
    public boolean isBuilding() {
        return building;
    }
}
