package hudson.restapi.model;

public class Artifact {
    private String jobName;
    private String fileName;
    private String href;
    
    public Artifact() {}

    @SuppressWarnings("rawtypes")
    public Artifact(String jobName, hudson.model.Run.Artifact artifact) {
        this.jobName = jobName;
        this.setFileName(artifact.getFileName());
        this.setHref(artifact.getHref());
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getHref() {
        return href;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobName() {
        return jobName;
    }
}
