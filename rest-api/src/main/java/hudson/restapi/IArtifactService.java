package hudson.restapi;

import hudson.restapi.model.Artifact;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * Represents a REST-ful interface for interacting with jobs and builds.
 * 
 * @author Gabe McArthur <madeonamac@gmail.com>
 */
public interface IArtifactService {
    @GET
    @Path("/jobs/{jobName}/builds/{buildNumber}/artifacts/")
    @Produces("application/vnd.artifacts+json")
    List<Artifact> getArtifacts(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber);
    /*
    @GET
    @Path("/jobs/{jobName}/builds/{buildNumber}/artifacts/{artifactName}")
    File getArtifact(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber, @PathParam("artifactName") final String artifactName);
*/
    
    @GET
    @Path("/artifacts")
    List<Artifact> getAllArtifacts();
    
    @GET
    @Path("/artifacts/latest")
    List<Artifact> getLatestArtifacts();
}
