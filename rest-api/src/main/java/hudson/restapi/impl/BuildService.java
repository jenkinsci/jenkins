package hudson.restapi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import com.google.inject.Inject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.Queue.WaitingItem;
import hudson.restapi.IBuildService;
import hudson.restapi.model.Build;
import hudson.restapi.model.LogPart;
import hudson.restapi.repos.JobRepository;
import hudson.security.Permission;

public class BuildService implements IBuildService {
    private final Hudson hudson;
    private final JobRepository repo;
    
    @Inject
    public BuildService(Hudson hudson, JobRepository repo) {
        this.hudson = hudson;
        this.repo = repo;
    }
    
    @SuppressWarnings("rawtypes")
    public List<Build> getAllBuilds(final String jobName) {
        List<Build> builds = new ArrayList<Build>();
        hudson.model.Job job = repo.getJob(jobName, Permission.READ);
        for (Object orun : job.getBuilds()) {
            Run run = (Run)orun;
            builds.add(new Build(run));
        }
        return builds;
    }

    public int newBuild(final String jobName) {
        @SuppressWarnings("rawtypes")
        AbstractProject project = repo.getProject(jobName, Permission.WRITE);
        WaitingItem item = hudson.getQueue().schedule(project);
        if (item == null) {
            throw new WebApplicationException(Response.Status.PRECONDITION_FAILED);
        }
        
        return project.getNextBuildNumber();
    }

    public Build getBuild(final String jobName, final int buildNumber) {
        @SuppressWarnings("rawtypes")
        Run run = repo.getRun(jobName, buildNumber);
        
        if (run == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        
        return new Build(run);
    }

    public StreamingOutput getBuildLog(final String jobName, final int buildNumber) {
        return new StreamingOutput() {
            public void write(OutputStream output) throws IOException, WebApplicationException {
                @SuppressWarnings("rawtypes")
                Run run = repo.getRun(jobName, buildNumber);
                IOUtils.copy(run.getLogInputStream(), output);
            }
        };
    }

    public LogPart getBuildLog(final String jobName, final int buildNumber, final long offset) {
        @SuppressWarnings("rawtypes")
        Run run = repo.getRun(jobName, buildNumber);
        if (run.hasntStartedYet())
            return new LogPart("", false, 0);
        
        InputStream stream;
        
        try {
            stream = run.getLogInputStream();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
        }
        
        try {
            if (offset > 0 && stream.skip(offset) <= 0) {
                return new LogPart("", !run.isBuilding(), offset);
            }
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        
        final int bufferSize = 4096;
        long wrote = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        try {
            byte[] copyBuffer = new byte[bufferSize];

            wrote = stream.read(copyBuffer, 0, bufferSize);
            if (wrote > 0)
                output.write(copyBuffer, 0, (int)wrote);
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.SERVICE_UNAVAILABLE);
        }

        return new LogPart(output.toString(), !run.isBuilding(), offset + wrote);
    }

    public void retainBuild(String jobName, int buildNumber) {
        @SuppressWarnings("rawtypes")
        Run run = repo.getRun(jobName, buildNumber);
        try {
            run.keepLog();
            run.save();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }

    public void deleteBuild(String jobName, int buildNumber) {
        @SuppressWarnings("rawtypes")
        Run run = repo.getRun(jobName, buildNumber);
        try {
            run.delete();
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.NOT_MODIFIED);
        }
    }
}
