package hudson.restapi.model;

public class LogPart {
    private String content;
    private boolean finished;
    private long nextOffset;
    
    public LogPart() { }
    public LogPart(String content, boolean finished, long nextOffeset) {
        this.content = content;
        this.finished = finished;
        this.nextOffset = nextOffeset;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setFinished(boolean finished) {
        this.finished = finished;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    public void setNextOffset(long nextOffset) {
        this.nextOffset = nextOffset;
    }
    
    public long getNextOffset() {
        return nextOffset;
    }
}
