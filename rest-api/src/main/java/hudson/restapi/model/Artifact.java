package hudson.restapi.model;

public class Artifact {
    private String fileName;
    private String href;
    
    public Artifact() {}

    @SuppressWarnings("rawtypes")
    public Artifact(hudson.model.Run.Artifact artifact) {
        this.fileName = artifact.getFileName();
        this.href = artifact.getHref();
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
}
