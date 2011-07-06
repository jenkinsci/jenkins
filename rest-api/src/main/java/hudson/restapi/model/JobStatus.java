package hudson.restapi.model;

import hudson.model.Run;

public class JobStatus {
    private String name;
    private String description;
    private int lastBuildNumber;
    private boolean isBuilding;
    private boolean inQueue;
    
    public JobStatus() { }
    
    @SuppressWarnings("rawtypes") 
    public JobStatus(hudson.model.Job job) {
        this.setName(job.getName());
        this.setDescription(job.getDescription());
        
        Run lastBuild = job.getLastBuild();
        if (lastBuild != null) {
            this.setLastBuildNumber(lastBuild.getNumber());
        }
        
        setBuilding(job.isBuilding());
        setInQueue(job.isInQueue());
    }
    
    public JobStatus(String name, String description, int lastBuildNumber, boolean isBuilding, boolean inQueue) {
        this.name = name;
        this.description = description;
        this.lastBuildNumber = lastBuildNumber;
        this.isBuilding = isBuilding;
        this.inQueue = inQueue;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setLastBuildNumber(int lastBuildNumber) {
        this.lastBuildNumber = lastBuildNumber;
    }

    public int getLastBuildNumber() {
        return lastBuildNumber;
    }

    public void setBuilding(boolean building) {
        this.isBuilding = building;
    }

    public boolean isBuilding() {
        return isBuilding;
    }

    public void setInQueue(boolean inQueue) {
        this.inQueue = inQueue;
    }

    public boolean isInQueue() {
        return inQueue;
    }
}
