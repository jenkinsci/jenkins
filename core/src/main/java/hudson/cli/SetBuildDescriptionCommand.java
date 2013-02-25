package hudson.cli;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.remoting.Callable;

import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;

@Extension
public class SetBuildDescriptionCommand extends CLICommand implements Serializable {

    @Override
    public String getShortDescription() {
        return Messages.SetBuildDescriptionCommand_ShortDescription();
     }

    @Argument(metaVar="JOB",usage="Name of the job to build",required=true,index=0)
    public transient AbstractProject<?,?> job;

    @Argument(metaVar="BUILD#",usage="Number of the build",required=true,index=1)
    public int number;
    
    @Argument(metaVar="DESCRIPTION",required=true,usage="Description to be set. '=' to read from stdin.", index=2)
    public String description;

    protected int run() throws Exception {
    	Run run = job.getBuildByNumber(number);
        run.checkPermission(Run.UPDATE);

        if ("=".equals(description)) {
        	description = IOUtils.toString(stdin);
        }
        
        run.setDescription(description);
        
        return 0;
    }
    
}
