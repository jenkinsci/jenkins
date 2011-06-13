package hudson.restapi.model;

import hudson.logging.LogRecorder;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JobInfo {
    public class RetentionPlan {
        private boolean retainOldBuilds;
        private int maxBuildsToKeep;
        
        public RetentionPlan(boolean retainOldBuilds, int maxBuildsToKeep) {
            this.retainOldBuilds = retainOldBuilds;
            this.maxBuildsToKeep = maxBuildsToKeep;
        }
        
        public void setRetainOldBuilds(boolean retainOldBuilds) {
            this.retainOldBuilds = retainOldBuilds;
        }
        public boolean isRetainOldBuilds() {
            return retainOldBuilds;
        }
        public void setMaxBuildsToKeep(int maxBuildsToKeep) {
            this.maxBuildsToKeep = maxBuildsToKeep;
        }
        public int getMaxBuildsToKeep() {
            return maxBuildsToKeep;
        }
    }
    
    public class SourceControl {
        private String type;
        private String url;
        private String localPath;
        
        public SourceControl(String type, String url, String localPath) {
            this.type = type;
            this.url = url;
            this.localPath = localPath;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getType() {
            return type;
        }
        public void setUrl(String url) {
            this.url = url;
        }
        public String getUrl() {
            return url;
        }
        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }
        public String getLocalPath() {
            return localPath;
        }
    }
    
    public class BuildTrigger {
        public static final String POLL_SCM = "poll";
        public static final String PERIODICALLY = "periodically";
        public static final String FOLLOWING = "following";
        
        private String type;
        private String schedule;
        
        public BuildTrigger(String type, String schedule) {
            super();
            this.type = type;
            this.schedule = schedule;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }

        public String getSchedule() {
            return schedule;
        }
    }
    
    public class BuildStep {
        public static final String SHELL = "shell";
        public static final String MAVEN = "maven";
        
        private String type;
        private List<String> configuration;
        
        public BuildStep(String type, List<String> configuration) {
            super();
            this.type = type;
            this.configuration = configuration;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getType() {
            return type;
        }
        public void setConfiguration(List<String> configuration) {
            this.configuration = configuration;
        }
        public List<String> getConfiguration() {
            return configuration;
        }
    }
    
    public class ArchivalBehavior {
        private boolean archived;
        private String pattern;
        
        public ArchivalBehavior(boolean archived, String pattern) {
            super();
            this.archived = archived;
            this.pattern = pattern;
        }

        public void setArchived(boolean archived) {
            this.archived = archived;
        }

        public boolean isArchived() {
            return archived;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }
    }
    
    private String jobName;
    private RetentionPlan retentionPlan;
    private SourceControl scm;
    private List<BuildTrigger> triggers;
    private List<BuildStep> steps;
    private ArchivalBehavior archivalBehavior;
    
    public JobInfo(String jobName, 
                   RetentionPlan retentionPlan, 
                   SourceControl scm, 
                   List<BuildTrigger> triggers, 
                   List<BuildStep> steps,
                   ArchivalBehavior archivalBehavior) {
        this.jobName = jobName;
        this.retentionPlan = retentionPlan;
        this.scm = scm;
        this.triggers = triggers;
        this.steps = steps;
        this.setArchivalBehavior(archivalBehavior);
    }

    public JobInfo(Job job) {
        this.jobName = job.getName();
        
        if (job.getLogRotator() != null) {
            this.retentionPlan = new RetentionPlan(true, job.getLogRotator().getNumToKeep());
        } else {
            this.retentionPlan = new RetentionPlan(false, 0);
        }
        
        this.triggers = new ArrayList<BuildTrigger>();
        for (Action action : job.getActions()) {
            this.triggers.add(new BuildTrigger(action.getDisplayName(), "")); //FIXME
        }
        
        this.steps = new ArrayList<BuildStep>();
        //Hudson.getInstance().get
        //for (Object obj : job.
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobName() {
        return jobName;
    }

    public void setRetentionPlan(RetentionPlan retentionPlan) {
        this.retentionPlan = retentionPlan;
    }

    public RetentionPlan getRetentionPlan() {
        return retentionPlan;
    }

    public void setScm(SourceControl scm) {
        this.scm = scm;
    }

    public SourceControl getScm() {
        return scm;
    }

    public void setTrigger(List<BuildTrigger> triggers) {
        this.triggers = triggers;
    }

    public List<BuildTrigger> getTrigger() {
        return triggers;
    }

    public void setSteps(List<BuildStep> steps) {
        this.steps = steps;
    }

    public List<BuildStep> getSteps() {
        return steps;
    }

    public void setArchivalBehavior(ArchivalBehavior archivalBehavior) {
        this.archivalBehavior = archivalBehavior;
    }

    public ArchivalBehavior getArchivalBehavior() {
        return archivalBehavior;
    }
}
