package org.jenkinsci.plugins.cli.job;
/**
 * @author aju.balachandran
 * @see This class used to list the Jobs in command line interface.
 */
import java.util.*;

import hudson.Extension;
import hudson.model.*;
import hudson.views.*;
import hudson.cli.CLICommand;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;


@Extension
public class ListJobsCommand extends CLICommand{

	public String getShortDescription() {
        return "Shows the Hudson Jobs";
    }
	
	@Argument(required = false,metaVar="JOB NAME",usage="Display the Job details like Job Name", index=0)
	public String jobNameLike;
	
	enum Format {
	      XML, CSV, PLAIN, COLUMN
	 }
	@Option(name="-format",metaVar="FORMAT",usage="Controls how the output from this command is printed.")
	private Format format = Format.COLUMN;

	@Option(name="-v",usage="verbose output")
	private boolean verbose;
		
	protected int run() {
		
		LastDurationColumn.DescriptorImpl lastDurColName = new LastDurationColumn.DescriptorImpl();
		LastFailureColumn.DescriptorImpl lastFailureColName = new LastFailureColumn.DescriptorImpl();
		LastSuccessColumn.DescriptorImpl lastSucColName = new LastSuccessColumn.DescriptorImpl();
		JobColumn.DescriptorImpl jobColName = new JobColumn.DescriptorImpl(); 
		StatusColumn.DescriptorImpl statusColName = new StatusColumn.DescriptorImpl();
		WeatherColumn.DescriptorImpl whetherColName = new WeatherColumn.DescriptorImpl();
		
		StringBuilder sb = new StringBuilder();
		if(!verbose && format == Format.COLUMN)
		{
			sb.append("=============================================================================");
			sb.append("\n"+statusColName.getDisplayName()+"\t"+whetherColName.getDisplayName()+"\t"+jobColName.getDisplayName()+"\t\tDisabled\tNode Name");
			sb.append("\n===========================================================================");
		}
		
		switch(format)
		{
		case XML :
			sb.append("     <listJobs>");
			break;
		case CSV :
			sb.append(statusColName.getDisplayName()+","+whetherColName.getDisplayName()+","+jobColName.getDisplayName()+",LastSuccess,LastSuccessBuild,LastFailure,LastFailureBuild,LastDuration,Disabled,NodeName");
			break;
		}
		
		String lastSuccess = "N/A";
		String lastFailure = "N/A";
		String lastDuration = "N/A";
		
		String status = "PENDING",upStreamProject = "",downStreamProject = "";
		String whether = "",lastSuccessBuildNo="",lastFailureBuildNo="";
		String disabled = "",assignedNode = "";
		long count = 0;
		boolean startSymbol = false,endSymbol = false;
		String jobName="";
		//cmd for list-jobs v 
		
		if(jobNameLike != null && !jobNameLike.trim().equals("") && jobNameLike.contains("*"))
		{
			if(jobNameLike.startsWith("*"))//* symbol at the starting point of the string
			{
				startSymbol = true;
				jobName = jobNameLike.substring(1);
			}
			else if(jobNameLike.endsWith("*"))
			{
				endSymbol = true;
				jobName = jobNameLike.substring(0,jobNameLike.indexOf("*"));
			}
			
		}
		else if(jobNameLike != null && !jobNameLike.trim().equals("") && !jobNameLike.contains("*"))
		{	
				jobName = jobNameLike;
		}
		String jobFullName = "";
		for (Job j : Hudson.getInstance().getAllItems(Job.class))
        {
			
			if(startSymbol)
			{
				if(j.getFullName().endsWith(jobName))
				{
					jobFullName = j.getFullName();
				}
			}
			else if(endSymbol)
			{
			
				if(j.getFullName().startsWith(jobName))
				{
					jobFullName = j.getFullName();
				}
			}
			else if(jobName != null && !"".equals(jobName))
			{
				if(j.getFullName().equals(jobName))
				{
					jobFullName = j.getFullName();
				}
			}
			else
			{
				jobFullName = j.getFullName();
			}
			if(jobFullName.equals(""))
			{
				continue;
			}
			
			Job jj = Hudson.getInstance().getItemByFullName(jobFullName,Job.class);
			Run r1 = jj.getLastBuild();//get the status and duration
			Run r2 = jj.getLastSuccessfulBuild();
			Run r3 = jj.getLastFailedBuild();
			
			AbstractProject ap = (AbstractProject)jj;
			assignedNode = (ap.getAssignedLabel()==null?"Master":ap.getAssignedLabel().getName());
			
			List<AbstractProject> upStreamList = ap.getUpstreamProjects();
			for(AbstractProject upStreamProj : upStreamList)
			{
				upStreamProject += upStreamProj.getName()+",";
			}
			if(upStreamProject.endsWith(","))
			{
				upStreamProject = upStreamProject.substring(0, upStreamProject.length()-1);
			}
			List<AbstractProject> downStreamList = ap.getDownstreamProjects();
			for(AbstractProject downStreamProj : downStreamList)
			{
				downStreamProject += downStreamProj.getName()+",";
			}
			if(downStreamProject.endsWith(","))
			{
				downStreamProject = downStreamProject.substring(0, downStreamProject.length()-1);
			}
			
			if(r1 != null)
			{
				status = r1.getResult().toString();
				lastDuration = r1.getDurationString();
				int percentage = 0;
				HealthReport rep = jj.getBuildHealth();//get the whether(score)
				percentage = rep.getScore();
				whether = String.valueOf(percentage==100?"1":percentage>=75?"2":percentage>=50?"3":percentage>=25?"4":"5");
				
				lastSuccess = (r2==null?"N/A":r2.getTimestampString()+"(#"+r2.getNumber()+")");
				lastFailure = (r3==null?"N/A":r3.getTimestampString()+"(#"+r3.getNumber()+")");
				
			}
			else
			{
								
			}
			//plain format
			if(verbose)
			{
				sb.append(jobFullName+"\n\t"+statusColName.getDisplayName()+"             : "+status);
				sb.append("\n\t"+whetherColName.getDisplayName()+"            : "+whether+"\n\t"+lastSucColName.getDisplayName()+"       : "+lastSuccess);
				sb.append("\n\t"+lastFailureColName.getDisplayName()+"       : "+lastFailure+"\n\t"+lastDurColName.getDisplayName()+"      : "+lastDuration);
				sb.append("\n\tDisabled           : "+String.valueOf(ap.isDisabled())+"\n\tNode Name          : "+assignedNode);
				sb.append("\n\tDownStream Project : "+downStreamProject+"\n\tUpStream Project   : "+upStreamProject+"\n");
			}
			else if(!verbose && format == Format.COLUMN)
			{
				String jobStat = "N/A";
				if(!status.trim().equalsIgnoreCase("N/A"))
				{
					jobStat = status.substring(0, 1);
				}
				sb.append("\n"+jobStat+"\t"+whether+"\t"+jobFullName+"\t\t"+String.valueOf(ap.isDisabled())+"\t"+assignedNode);
			}
			else
			{
				if(r2 != null)
				{
					lastSuccess = r2.getTimestampString();
					lastSuccessBuildNo = String.valueOf(r2.getNumber());
				}
				if(r3!=null)
				{
					lastFailure = r3.getTimestampString();
					lastFailureBuildNo = String.valueOf(r3.getNumber());
				}
				switch(format)
				{
				
				case CSV ://csv format
					
					sb.append("\n"+status+","+whether+","+jobFullName+","+lastSuccess+","+lastSuccessBuildNo+","+lastFailure+","+lastFailureBuildNo+","+lastDuration+","+String.valueOf(ap.isDisabled())+","+assignedNode);
					break;
					
				case XML ://xml format
					
					sb.append("\n\t<job>");
					sb.append("\n\t  <name>"+jobFullName+"</name>");
					sb.append("\n\t  <status>"+status+"</status>");
					sb.append("\n\t  <weather>"+whether+"</weather>");
					sb.append("\n\t  <lastSuccess>"+lastSuccess+"</lastSuccess>");
					sb.append("\n\t  <lastSuccessBuild>"+lastSuccessBuildNo+"</lastSuccessBuild>");
					sb.append("\n\t  <lastFailure>"+lastFailure+"</lastFailure>");
					sb.append("\n\t  <lastFailureBuild>"+lastFailureBuildNo+"</lastFailureBuild>");
					sb.append("\n\t  <lastDuration>"+lastDuration+"</lastDuration>");
					sb.append("\n\t  <disabled>"+String.valueOf(ap.isDisabled())+"</disabled>");
					sb.append("\n\t  <node>"+assignedNode+"</node>");
					sb.append("\n\t</job>");
					break;
					
				case PLAIN ://plain format
					
					sb.append("\n"+jobFullName+"\n\t"+statusColName.getDisplayName()+"             : "+status);
					sb.append("\n\t"+whetherColName.getDisplayName()+"            : "+whether+"\n\t"+lastSucColName.getDisplayName()+"       : "+lastSuccess+"(#"+lastSuccessBuildNo+")");
					sb.append("\n\t"+lastFailureColName.getDisplayName()+"       : "+lastFailure+"(#"+lastFailureBuildNo+")\n\t"+lastDurColName.getDisplayName()+"      : "+lastDuration);
					sb.append("\n\tDisabled           : "+String.valueOf(ap.isDisabled())+"\n\tNode Name          : "+assignedNode);
					sb.append("\n\tDownStream Project : "+downStreamProject+"\n\tUpStream Project   : "+upStreamProject+"\n");
				}
			}
        }	
		if(format == Format.XML)
		{
			sb.append("\n     </listJobs>");
		}
		
		if(true)
		{
			stdout.println(sb.toString());
			return 0;
		}
        return 0;
    }

}
