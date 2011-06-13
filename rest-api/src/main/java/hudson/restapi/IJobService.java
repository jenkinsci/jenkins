package hudson.restapi;

import java.util.List;
import hudson.restapi.model.JobInfo;
import hudson.restapi.model.JobStatus;
import javax.ws.rs.Consumes;
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
    List<JobStatus> getAllJobs();
    
    @POST
    @Path("/")
    @Consumes("application/vnd.job+json")
    void createJob(final JobInfo jobInfo);
    
    @GET
    @Path("/{jobName}")
    @Produces("application/vnd.job+json")
    JobInfo getJob(@PathParam("jobName") final String jobName);
    
    @PUT
    @Path("/{jobName}")
    @Consumes("application/vnd.job+json")
    void updateJob(@PathParam("jobName") final String jobName, final JobInfo jobInfo);
    
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
