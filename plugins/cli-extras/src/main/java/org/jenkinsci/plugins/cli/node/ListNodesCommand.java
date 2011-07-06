package org.jenkinsci.plugins.cli.node;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hudson.Util;
import hudson.model.*;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.util.ClockDifference;
import hudson.util.TimeUnit2;
import hudson.util.IOException2;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.FilePath.FileCallable;

import hudson.node_monitors.ResponseTimeMonitor;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.FilePath;
import hudson.views.*;
import hudson.slaves.SlaveComputer;
import hudson.slaves.OfflineCause;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import com.sun.management.*;


@Extension
public class ListNodesCommand extends CLICommand{

	public String getShortDescription() {
        return "Shows Node List";
    }

	 enum Format {
	      XML, CSV, PLAIN, COLUMN
	 }
	@Option(name="-format",metaVar="FORMAT",usage="xml ,csv or plain format")
	public Format format = Format.COLUMN;

	@Option(name="-v",usage="verbose output")
	private boolean verbose;

	
	protected int run() throws Exception{
//		stdout.println(verbose);
		List<Slave> slaves = Hudson.getInstance().getSlaves();
		Computer c[] = Hudson.getInstance().getComputers();
		String dispName = c[0].getDisplayName();
		String noOfExecutors = String.valueOf(c[0].getNumExecutors());
		File homeDir = new File(System.getProperty("user.home"));
		String freeSpace = "";
		long freeSpaceInByte = homeDir.getUsableSpace();
		freeSpace = NodeService.getInstance().getSpeceConversion(freeSpaceInByte);
        long freeTempSpaceInByte = homeDir.getFreeSpace();
        String freeTempSpaceMaster = NodeService.getInstance().getSpeceConversion(freeTempSpaceInByte);
       
        OperatingSystemMXBean mxbean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		 
        long swapSpaceInByte = mxbean.getFreeSwapSpaceSize();
        long swapSpaceInMb = swapSpaceInByte/(1024*1024);
        String swapSpace = String.valueOf(swapSpaceInMb)+" MB";
        
        NodeService.Data masterData = NodeService.getInstance().responceTimeOfMaster(c[0]);
        StringBuilder sb = new StringBuilder();
		switch(format)
		{
			case XML :
//				stdout.println("     <nodeList>");
				sb.append("     <nodeList>");
			break;
			case CSV :
//				stdout.println("name,status,noOfExcecutors,Architecture,ClockDiff,FreeDiskSpace,ResponseTime,FreeTempSpace,FreeSwapSpace,Label");
				sb.append("name,status,noOfExcecutors,Architecture,ClockDiff,FreeDiskSpace,ResponseTime,FreeTempSpace,FreeSwapSpace,Label");
			break;
		}

		if(!verbose && format == Format.COLUMN)
		{
//			stdout.println("");
			sb.append("List of Nodes");
			sb.append("\n======================================================================");

			sb.append("\nStatus\tName\tArchitecture\tClockDiff.\tFreeDiskSpace");
			sb.append("\n======================================================================");
//			sb.append("\n");
		}
		
		long avgTime = -1;
		Map<Object,Object> sysProp = new HashMap<Object,Object>();
		if(!verbose && format == Format.COLUMN)
		{
			sb.append( "\nonline\t"+dispName+"\t"+NodeService.getInstance().getArchMaster()+"\t"+
					new ClockDifference(0).toString()+"\t"+freeSpace);
//			sb.append("\n");
		}
		else if(verbose)
		{

			sb.append(c[0].getCaption());
			sb.append("\n\tStatus       :"+"online");
			sb.append("\n\tLabel        :"+dispName);
			sb.append("\n\tNoOfExec     :"+noOfExecutors);
			sb.append("\n\tArchitecture :"+NodeService.getInstance().getArchMaster());
			sb.append("\n\tClockDiff.   :"+new ClockDifference(0).toString());
			sb.append("\n\tFreeDiskSpace:"+freeSpace);
			sb.append("\n\tResponseTime :"+masterData);
			sb.append("\n\tFreeTempSpace:"+freeTempSpaceMaster);
			sb.append("\n\tFreeSwapSpace:"+swapSpace);
		}
		else
		{
			switch(format)
			{
				case XML : 
			

					sb.append("\n\t<node>");
					sb.append("\n\t  <name>"+c[0].getCaption()+"</name>");
					sb.append("\n\t  <status>"+"online"+"</status>");
					sb.append("\n\t  <noOfExec>"+noOfExecutors+"</noOfExec>");
					sb.append("\n\t  <architecture>"+NodeService.getInstance().getArchMaster()+"</architecture>");
					sb.append("\n\t  <clockDiff>"+new ClockDifference(0).toString()+"</clockDiff>");
					sb.append("\n\t  <freeDiskSpace>"+freeSpace+"</freeDiskSpace>");
					sb.append("\n\t  <responseTime>"+masterData+"</responseTime>");
					sb.append("\n\t  <freeTempSpace>"+freeTempSpaceMaster+"</freeTempSpace>");
					sb.append("\n\t  <freeSwapSpace>"+swapSpace+"</freeSwapSpace>");
					sb.append("\n\t  <label>"+dispName+"</label>");
					sb.append("\n\t</node>");
					
				break;
				case CSV :
				
					sb.append("\n"+c[0].getCaption()+","+"online"+","+noOfExecutors+","+NodeService.getInstance().getArchMaster()+","+new ClockDifference(0).toString()+","+freeSpace+","+masterData+","+freeTempSpaceMaster+","+swapSpace+","+dispName);
				break;
				case PLAIN :
					
					sb.append(c[0].getCaption());
					sb.append("\n\tStatus       :"+"online");
					sb.append("\n\tLabel        :"+dispName);
					sb.append("\n\tNoOfExec     :"+noOfExecutors);
					sb.append("\n\tArchitecture :"+NodeService.getInstance().getArchMaster());
					sb.append("\n\tClockDiff.   :"+new ClockDifference(0).toString());
					sb.append("\n\tFreeDiskSpace:"+freeSpace);
					sb.append("\n\tResponseTime :"+masterData);
					sb.append("\n\tFreeTempSpace:"+freeTempSpaceMaster);
					sb.append("\n\tFreeSwapSpace:"+swapSpace);
				break;
				
			}
		}
		for (Slave s : slaves) {
			SlaveComputer sc = s.getComputer();
			String status = "",freeTempSpace = "N/A",freeSwapSpace = "N/A",response = "N/A";
			String clockDiff = "N/A",arch = "N/A",freeDiskSpace = "N/A";	
			try
			{
				ClockDifference cd = s.getClockDifference();
				clockDiff = cd.toString();
				arch = s.getChannel().call(new NodeService.GetArchTask());
				Computer comp = s.toComputer();
				
				NodeService.Data d = NodeService.responceTime(s);

	            FilePath p = comp.getNode().getRootPath();
	            freeTempSpace = p.act(new NodeService.GetTempSpace());
				freeSwapSpace = s.getChannel().call(new NodeService.GetSwapSpace());
				
				freeDiskSpace = p.act(new NodeService.GetUsableSpace());

				response = d.toString();
	           
			}
			catch (Exception e) {
				System.out.println(e.toString());
			}
			if (sc.isOffline()) {
				status = "offline";
			} else {
				status = "online";
			}
			if(!verbose && format == Format.COLUMN)
			{
				sb.append("\n"+status + "\t" + s.getNodeName() + "\t"
						+ arch+"\t"+clockDiff+"\t"+freeDiskSpace);
//				sb.append("\n");
			}
			else if(verbose)
			{

				sb.append("\n"+s.getNodeName());
				sb.append("\n\tStatus       :"+status);
				sb.append("\n\tLabel        :"+s.getLabelString());
				sb.append("\n\tNoOfExec     :"+sc.getNumExecutors());
				sb.append("\n\tArchitecture :"+arch);
				sb.append("\n\tClockDiff.   :"+clockDiff);
				sb.append("\n\tFreeDiskSpace:"+freeDiskSpace);
				sb.append("\n\tResponseTime :"+response);
				sb.append("\n\tFreeTempSpace:"+freeTempSpace);
				sb.append("\n\tFreeSwapSpace:"+freeSwapSpace);
			}
			else
			{
				switch(format)
				{
					case XML : 
				
						sb.append("\n\t<node>");
						sb.append("\n\t  <name>"+s.getNodeName()+"</name>");
						sb.append("\n\t  <status>"+status+"</status>");
						sb.append("\n\t  <noOfExec>"+sc.getNumExecutors()+"</noOfExec>");
						sb.append("\n\t  <architecture>"+arch+"</architecture>");
						sb.append("\n\t  <clockDiff>"+clockDiff+"</clockDiff>");
						sb.append("\n\t  <freeDiskSpace>"+freeDiskSpace+"</freeDiskSpace>");
						sb.append("\n\t  <responseTime>"+response+"</responseTime>");
						sb.append("\n\t  <freeTempSpace>"+freeTempSpace+"</freeTempSpace>");
						sb.append("\n\t  <freeSwapSpace>"+freeSwapSpace+"</freeSwapSpace>");
						sb.append("\n\t  <label>"+s.getLabelString()+"</label>");
						sb.append("\n\t</node>");
					break;
					case CSV :
					
						sb.append("\n"+s.getNodeName()+","+status+","+sc.getNumExecutors()+","+arch+","+clockDiff+","+freeDiskSpace+","+freeTempSpace+","+freeSwapSpace+","+s.getLabelString());
					break;
					case PLAIN :

						sb.append("\n"+s.getNodeName());
						sb.append("\n\tStatus       :"+status);
						sb.append("\n\tLabel        :"+s.getLabelString());
						sb.append("\n\tNoOfExec     :"+sc.getNumExecutors());
						sb.append("\n\tArchitecture :"+arch);
						sb.append("\n\tClockDiff.   :"+clockDiff);
						sb.append("\n\tFreeDiskSpace:"+freeDiskSpace);
						sb.append("\n\tResponseTime :"+response);
						sb.append("\n\tFreeTempSpace:"+freeTempSpace);
						sb.append("\n\tFreeSwapSpace:"+freeSwapSpace);
					break;
					
				}
				
			}

			
		}
		switch(format)
		{
			case XML : 
			
//				stdout.println("     </nodeList>");
				sb.append("\n     </nodeList>");
				
			break;
			
		}
		if(true)
		{
			stdout.println(sb.toString());
			return 0;
		}
		return 0;
	
	}
	
}
