package hudson.restapi.guice;

import hudson.model.Hudson;
import hudson.restapi.impl.ArtifactService;
import hudson.restapi.impl.BuildService;
import hudson.restapi.impl.JobService;
import hudson.restapi.repos.JobRepository;

import com.google.inject.Module;
import com.google.inject.Binder;

public class RestAPIModule implements Module {
    public void configure(Binder binder) {
        binder.bind(Hudson.class).toProvider(HudsonProvider.class);
        
        // Repositories
        binder.bind(JobRepository.class);
        
        // Services
        binder.bind(JobService.class);
        binder.bind(BuildService.class);
        binder.bind(ArtifactService.class);
    }
}
