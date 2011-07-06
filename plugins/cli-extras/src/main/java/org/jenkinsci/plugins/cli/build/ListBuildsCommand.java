package org.jenkinsci.plugins.cli.build;
/**
 * @author aju.balachandran
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import hudson.cli.CLICommand;
import hudson.model.*;
import hudson.Extension;
import hudson.tasks.*;
import hudson.DescriptorExtensionList;
import hudson.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jenkinsci.plugins.cli.common.Commons;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;


@Extension
public class ListBuildsCommand extends CLICommand{

	public String getShortDescription() {
        return "Shows Build List For A Job";
    }
	@Argument(required = true,metaVar="Job Name",usage="Name of the Job",index=0)
	public String jobName;
	

	@Option(name="-n",usage="Display Number Of Bilds From Last")
	public String noOfBuilds = "20";
	
	enum Format {
	     XML, CSV, PLAIN
	}

	@Option(name="-format",usage="Controls how the output from this command is printed.")
	public Format format = Format.PLAIN;

	protected int run() {
		
//		BuildDetailsOfJob bj = new BuildDetailsOfJob(); 
		StringBuilder sb = new StringBuilder();	
		
		try
		{

			TopLevelItem job1 = Hudson.getInstance().getItem(jobName);
			Job job = (Job)Hudson.getInstance().getItem(jobName);
			AbstractProject ap = (AbstractProject)job;
			String slaveName = (ap.getAssignedLabel()==null?"Master":ap.getAssignedLabel().getName());
			
			if(job == null)
			{
				sb.append("Incorrect Job Name : "+jobName);
				stdout.println(sb.toString());
				return 0;
			}
			RunList<Run> rl = (RunList<Run>)job.getBuilds();
			List<BuildDetailsDto> listBD = new ArrayList<BuildDetailsDto>();
			File jobBuildFolder = Hudson.getInstance().getRootDirFor(job1);
			File dir = new File(jobBuildFolder,"builds");
			for(Run r : rl)
			{
//				String s = r.getTimestampString2();
				Commons c = new Commons();
				File logFile = c.getLogFileForBuild(dir,r.getNumber());
				String log = c.getLogInfo(logFile);//get the trigger
				BuildDetailsDto bd = new BuildDetailsDto();
				bd.setBuildDuration(r.getDurationString());
				bd.setBuildResult(r.getResult().toString());
				bd.setBuildNumber(r.getNumber());
				bd.setBuildDateTime(r.getTimestampString2());
				bd.setSlaveName(slaveName);
				bd.setTrigger(log);
//				String s2 = (r.getIDFormatter()).toString();
//				String s3 = r.getId();
				listBD.add(bd);
			}
			long start = System.currentTimeMillis();
//			File jobBuildFolder = new File(new File(System.getProperty("user.home")),".hudson\\jobs\\"+jobName);
//			List<BuildDetailsDto> buildDetails =  bj.getDetails(jobBuildFolder.toString(),(noOfBuilds == null ? null : new Integer(noOfBuilds)));
			
			long end = System.currentTimeMillis();
					
//			if(buildDetails.isEmpty())
				if(listBD.isEmpty())
			{
				sb.append("No Details Found In The "+jobName);
			}
			else
			{
				switch(format)
				{
					case XML :
						sb.append("     <builds>\n");
						break;
					case CSV :
						sb.append("JobName,Status,Build#,DateTime,Duration,Slave,Trigger\n");
						break;
					case PLAIN :
						sb.append(jobName+"\n");
						break;
					default :
						sb.append("JobName\tStatus\tBuild#\tDateTime\tDuration\tSlave\tTrigger\n");
				}
				
				int count = 0;
//				for(BuildDetailsDto dto : buildDetails)
				for(BuildDetailsDto dto : listBD)	
				{
					count++;
					if(count>Integer.parseInt(noOfBuilds))
					{
						break;
					}
					String slave = "master";
					if(dto.getSlaveName()!= null && dto.getSlaveName().trim().equals(""))
					{
						slave = dto.getSlaveName();
					}
					switch(format)
					{
						case CSV :
							sb.append(jobName+","+dto.getBuildResult()+","+dto.getBuildNumber()+","+dto.getBuildDateTime()+","+dto.getBuildDuration()+","+slave+","+dto.getTrigger()+"\n");
							break;
						case XML :
							sb.append("\t<build>\n");
							sb.append("\t  <jobName>"+jobName+"</jobName>\n");
							sb.append("\t  <status>"+dto.getBuildResult()+"</status>\n");
							sb.append("\t  <number>"+dto.getBuildNumber()+"</number>\n");
							sb.append("\t  <dateTime>"+dto.getBuildDateTime()+"</dateTime>\n");
							sb.append("\t  <duration>"+dto.getBuildDuration()+"</duration>\n");
							sb.append("\t  <slave>"+slave+"</slave>\n");
							sb.append("\t  <trigger>"+dto.getTrigger()+"</trigger>\n");
							sb.append("\t</build>\n");
							break;
						case PLAIN :
							sb.append("\t"+dto.getBuildNumber()+"\n");
							sb.append("\t\tStatus    : "+dto.getBuildResult()+"\n");
							sb.append("\t\tDate&Time : "+dto.getBuildDateTime()+"\n");
							sb.append("\t\tDuration  : "+dto.getBuildDuration()+"\n");
							sb.append("\t\tSlave     : "+slave+"\n");
							sb.append("\t\tTrigger   : "+dto.getTrigger()+"\n");
							break;
						default :
							sb.append(jobName+"\t"+dto.getBuildResult().substring(0,1)+"\t"+dto.getBuildNumber()+"\t"+dto.getBuildDateTime()+"\t"+dto.getBuildDuration()+"\t"+slave+"\t"+dto.getTrigger()+"\n");
							break;
					}

				}
				switch(format)
				{
					case XML :
						sb.append("     </builds>");
						break;
					default :
						sb.delete(sb.lastIndexOf("\n"), sb.length());
				}
			}	
//		}catch (FileNotFoundException fnfe) {
//			sb.append("Error : Job Name is not correct.");
//		}catch (IOException e) {
//			
//			sb.append("Error : "+e.getMessage());
		}
		catch (Exception e) {
			
			sb.append(e.getMessage());
		}
		stdout.println(sb.toString());
		return 0;
	}
	
}
