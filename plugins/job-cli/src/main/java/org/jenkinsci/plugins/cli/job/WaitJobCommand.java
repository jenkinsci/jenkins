/**
 * @author aju.balachandran
 */
package org.jenkinsci.plugins.cli.job;

import hudson.model.*;
import hudson.cli.CLICommand;
import hudson.Extension;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;


@Extension
public class WaitJobCommand  extends CLICommand{

	public String getShortDescription() {
        return "Shows Waiting Hudson Job Build";
    }
	
	@Argument(required = true,metaVar="JOBNAME",usage="Name of the Job",index=0)
	public String jobName;
	
	@Option(name="-n",metaVar="BUILD NUMBER",usage="Build Number")
	public String buildNo;
	
	protected int run()throws Exception {
		String build = "";
		boolean flag = true;
		Job job = Hudson.getInstance().getItemByFullName(jobName,Job.class);
		try
		{
			job.getFirstBuild();
		}catch (NullPointerException e) {
			stdout.print("Error :- Invalid Job Name : "+jobName);
			return 0;
		}
		do
		{
//			stdout.print(".");
			if(buildNo != null && !buildNo.trim().equals(""))
			{
				if(Integer.parseInt(buildNo) == (job.getNextBuildNumber()-1))
				{
					flag = job.isBuilding();
				}
				else if(Integer.parseInt(buildNo) < job.getNextBuildNumber()-1)
				{
					flag = false;
				}
			}
			else
			{
				flag = job.isBuilding();
			}
			Thread.sleep(1000);//wait for 1 seconds
		}while(flag );
	return 0;	
	}
	
}