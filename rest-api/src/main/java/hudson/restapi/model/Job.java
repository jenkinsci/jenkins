package hudson.restapi.model;

import hudson.model.Run;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "job")
public class Job {
    private String name;
    private String description;
    private int lastBuildNumber;
    private boolean building;
    private boolean inQueue;
    
    public Job() { }
    
    @SuppressWarnings("rawtypes") 
    public Job(hudson.model.Job job) {
        this.setName(job.getName());
        this.setDescription(job.getDescription());
        
        Run lastBuild = job.getLastBuild();
        if (lastBuild != null) {
            this.setLastBuildNumber(lastBuild.getNumber());
        }
        
        setBuilding(job.isBuilding());
        setInQueue(job.isInQueue());
    }
    
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setLastBuildNumber(int lastBuildNumber) {
        this.lastBuildNumber = lastBuildNumber;
    }

    @XmlElement
    public int getLastBuildNumber() {
        return lastBuildNumber;
    }

    public void setBuilding(boolean building) {
        this.building = building;
    }

    @XmlElement
    public boolean isBuilding() {
        return building;
    }

    public void setInQueue(boolean inQueue) {
        this.inQueue = inQueue;
    }

    @XmlElement
    public boolean isInQueue() {
        return inQueue;
    }
}
