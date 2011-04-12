package hudson.restapi;

import java.util.List;
import hudson.restapi.model.Build;
import hudson.restapi.model.Job;
import hudson.restapi.model.LogPart;
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
@Path("/jobs")
public interface IJobService {
    @GET
    @Path("/")
    @Produces("application/xml")
    List<Job> getAllJobs();
    
    @GET
    @Path("/{jobName}")
    @Produces("application/xml")
    Job getJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}")
    void createJob(@PathParam("jobName") final Job job);
    
    @PUT
    @Path("/{jobName}")
    void updateJob(@PathParam("jobName") final String jobName);
    
    @DELETE
    @Path("/{jobName}")
    void deleteJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}/disable")
    void disableJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}/enable")
    void enableJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}/copy/{to}")
    void copyJob(@PathParam("jobName") final String jobName, @PathParam("to") final String to);
    
    @GET
    @Path("/{jobName}/builds")
    @Produces("application/xml")
    List<Build> getAllBuilds(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}/builds/kickoff")
    int kickoffBuild(@PathParam("name") final String jobName);
    
    @GET
    @Path("/{jobName}/builds/{buildNumber}")
    @Produces("application/xml")
    Build getBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber);
    
    @GET
    @Path("/{jobName}/builds/{buildNumber}/logfile")
    StreamingOutput getBuildLogFile(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber);
    
    @GET
    @Path("/{jobName}/builds/{buildNumber}/logfile/{offset}")
    @Produces("application/vnd.logpart+json")
    LogPart getBuildLogPart(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber, @PathParam("offset") final long offset);
    
    @POST
    @Path("/{jobName}/builds/{buildNumber}/retain")
    void retainBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber);
    
    @DELETE
    @Path("/{jobName}/builds/{buildNumber}")
    void deleteBuild(@PathParam("jobName") final String jobName, @PathParam("buildNumber") final int buildNumber);
}
