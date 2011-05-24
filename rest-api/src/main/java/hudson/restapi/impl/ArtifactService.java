package hudson.restapi.impl;

import java.util.ArrayList;
import java.util.List;
import com.google.inject.Inject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.restapi.IArtifactService;
import hudson.restapi.model.Artifact;
import hudson.restapi.repos.JobRepository;
import hudson.security.Permission;

public class ArtifactService implements IArtifactService {
    private final JobRepository repo;
    
    @Inject
    public ArtifactService(JobRepository repo) {
        this.repo = repo;
    }
    
    @SuppressWarnings("rawtypes")
    public List<Artifact> getArtifacts(String jobName, int buildNumber) {
        ArrayList<Artifact> artifacts = new ArrayList<Artifact>();
        Job job = repo.getJob(jobName, Permission.READ);
        Run run = repo.getRun(jobName, buildNumber);
        for (Object obj : run.getArtifacts()) {
            hudson.model.Run.Artifact artifact = (hudson.model.Run.Artifact)obj;
            artifacts.add(new Artifact(job.getName(), artifact));
        }
        return artifacts;
    }
/*
    public File getArtifact(String jobName, int buildNumber, String artifactName) {
        for (Object obj : repo.getRun(jobName, buildNumber).getArtifacts()) {
            @SuppressWarnings("rawtypes")
            hudson.model.Run.Artifact artifact = (hudson.model.Run.Artifact)obj;
            if (artifact.getFileName() == artifactName)
                return artifact.getFile();
        }
        
        throw new WebApplicationException(Status.NOT_FOUND);
    }
*/
    @SuppressWarnings("rawtypes")
    public List<Artifact> getAllArtifacts() {
        ArrayList<Artifact> artifacts = new ArrayList<Artifact>();
        for (Job job : repo.getJobs(Permission.READ)) {
            for (Object obj : job.getBuilds()) {
                Run run = (Run)obj;
                for (Object obj2 : run.getArtifacts()) {
                    hudson.model.Run.Artifact artifact = (hudson.model.Run.Artifact)obj2;
                    artifacts.add(new Artifact(job.getName(), artifact));
                }
            }
        }

        return artifacts;
    }

    @SuppressWarnings("rawtypes")
    public List<Artifact> getLatestArtifacts() {
        ArrayList<Artifact> artifacts = new ArrayList<Artifact>();
        for (Job job : repo.getJobs(Permission.READ)) {
            Run run = job.getLastBuild();
            for (Object obj2 : run.getArtifacts()) {
                hudson.model.Run.Artifact artifact = (hudson.model.Run.Artifact)obj2;
                artifacts.add(new Artifact(job.getName(), artifact));
            }
        }

        return artifacts;
    }
}
