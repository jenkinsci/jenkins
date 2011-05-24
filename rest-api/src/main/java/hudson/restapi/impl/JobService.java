package hudson.restapi.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import com.google.inject.Inject;
import hudson.model.Hudson;
import hudson.restapi.IJobService;
import hudson.restapi.model.Job;
import hudson.restapi.repos.JobRepository;
import hudson.security.Permission;

public class JobService implements IJobService {
    private final Hudson hudson;
    private final JobRepository repo;
    
    @Inject
    public JobService(Hudson hudson, JobRepository repo) {
        this.hudson = hudson;
        this.repo = repo;
    }
    
    @SuppressWarnings("rawtypes")
    public List<Job> getAllJobs() {
        List<Job> jobs = new ArrayList<Job>();
        for (hudson.model.Job job : repo.getJobs(Permission.READ)) {
            jobs.add(new Job(job));
        }
        return jobs;
    }

    public Job getJob(final String jobName) {
        return new Job(repo.getJob(jobName, Permission.READ));
    }

    public void createJob(final String job) {
        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }

    public void updateJob(final String jobName) {
        throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
    }

    public void deleteJob(final String jobName) {
        try {
            repo.getJob(jobName, Permission.DELETE).delete();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        } catch (InterruptedException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }

    public void disableJob(final String jobName) {
        try {
            @SuppressWarnings("rawtypes")
            hudson.model.AbstractProject project = repo.getProject(jobName, Permission.WRITE);
            project.disable();
            project.save();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }

    public void enableJob(final String jobName) {
        try {
            @SuppressWarnings("rawtypes")
            hudson.model.AbstractProject project = repo.getProject(jobName, Permission.WRITE);
            project.enable();
            project.save();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }

    public void copyJob(final String jobName, final String to) {
        try {
            @SuppressWarnings("rawtypes")
            hudson.model.AbstractProject project = repo.getProject(jobName, Permission.CREATE);
            hudson.copy(project, to);
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }
}
