package hudson.restapi;

import hudson.restapi.model.Build;
import hudson.restapi.model.LogPart;
import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.StreamingOutput;

/**
 * Represents a REST-ful interface for interacting with jobs and builds.
 * 
 * @author Gabe McArthur <madeonamac@gmail.com>
 */
@Path("/jobs/{jobName}/builds")
public interface IBuildService {
    @GET
    @Path("/")
    @Produces("application/vnd.builds+json")
    List<Build> getAllBuilds(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/")
    int newBuild(@PathParam("jobName") final String jobName);
    
    @GET
    @Path("/{buildNumber}")
    @Produces("application/vnd.build+json")
    Build getBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") int buildNumber);
    
    @GET
    @Path("/{buildNumber}/logfile")
    StreamingOutput getBuildLog(final String jobName, final int buildNumber);
    
    @GET
    @Path("/{buildNumber}/logpart/{offset}")
    @Produces("application/vnd.logpart+json")
    LogPart getBuildLog(@PathParam("jobName") final String jobName, @PathParam("buildNumber") int buildNumber, @PathParam("offset") final long offset);
    
    @PUT
    @Path("/{buildNumber}/retain")
    void retainBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") int buildNumber);
    
    @DELETE
    @Path("/{buildNumber}")
    void deleteBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") int buildNumber);
}
