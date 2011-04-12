package hudson.restapi.repos;

import java.util.ArrayList;
import java.util.List;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.security.ACL;
import hudson.security.Permission;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import com.google.inject.Inject;

public class JobRepository {
    private final Hudson hudson;
    
    @Inject
    public JobRepository(Hudson hudson) {
        this.hudson = hudson;
    }
    
    @SuppressWarnings("rawtypes")
    public AbstractProject getProject(String jobName, Permission goingToDo) {
        if (StringUtils.isEmpty(jobName)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        for (hudson.model.AbstractProject proj : hudson.getItems(AbstractProject.class)) {
            if (proj.getName().equals(jobName)) {
                checkACL(proj.getACL(), goingToDo);
                return proj;
            }
        }
        
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
    
    @SuppressWarnings("rawtypes")
    public List<Job> getJobs(Permission goingToDo) {
        List<Job> jobs = new ArrayList<Job>();
        
        for (hudson.model.AbstractProject proj : hudson.getItems(AbstractProject.class)) {
            checkACL(proj.getACL(), goingToDo);
            jobs.add(proj);
        }
        
        return jobs;
    }
    
    @SuppressWarnings("rawtypes")
    public Job getJob(String jobName, Permission goingToDo) {
        if (StringUtils.isEmpty(jobName)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        
        TopLevelItem item = hudson.getItem(jobName);
        if (item == null || !(item instanceof Job)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        
        hudson.model.Job job = (hudson.model.Job)item;
        checkACL(job.getACL(), goingToDo);
        
        return job;
    }
    
    @SuppressWarnings("rawtypes")
    public hudson.model.Run getRun(String jobName, int buildNumber, Permission permission) {
        Job job = getJob(jobName, permission);
        Run run = job.getBuildByNumber(buildNumber);

        if (run == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        
        return run;
    }
    
    @SuppressWarnings("rawtypes")
    public Run getRun(String jobName, int buildNumber) {
        return this.getRun(jobName, buildNumber, Permission.READ);
    }
    
    private void checkACL(ACL acl, Permission goingToDo) {
        Authentication auth = Hudson.getAuthentication();
        if (!acl.hasPermission(auth, goingToDo)) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }
}
