package hudson.restapi;

import java.util.List;
import hudson.restapi.model.Job;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * Represents a REST-ful interface for interacting with jobs and builds.
 * 
 * @author Gabe McArthur <madeonamac@gmail.com>
 */
@Path("/jobs")
public interface IJobService {
    @GET
    @Path("/")
    @Produces("application/vnd.jobs+json")
    List<Job> getAllJobs();
    
    @GET
    @Path("/{jobName}")
    @Produces("application/vnd.job+json")
    Job getJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}")
    void createJob(@PathParam("jobName") final String job);
    
    @PUT
    @Path("/{jobName}")
    void updateJob(@PathParam("jobName") final String jobName);
    
    @DELETE
    @Path("/{jobName}")
    void deleteJob(@PathParam("jobName") final String jobName);
    
    @PUT
    @Path("/{jobName}/disable")
    void disableJob(@PathParam("jobName") final String jobName);
    
    @PUT
    @Path("/{jobName}/enable")
    void enableJob(@PathParam("jobName") final String jobName);
    
    @POST
    @Path("/{jobName}/copy/{to}")
    void copyJob(@PathParam("jobName") final String jobName, @PathParam("to") final String to);
}
