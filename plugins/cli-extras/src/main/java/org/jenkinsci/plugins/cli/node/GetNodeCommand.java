package org.jenkinsci.plugins.cli.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.sun.management.*;


@Extension
public class GetNodeCommand extends CLICommand{

	public String getShortDescription() {
        return "Show the Node";
    }
	
	@Argument(required = true,metaVar="NODE NAME",usage="Name of Node (Slave)",index=0)
	public String nodeName;
	
    enum Format {
        XML, CSV, PLAIN, COLUMN
    }

    @Option(name="-format",metaVar="FORMAT",usage="Controls how the output from this command is printed.")
    public Format format = Format.COLUMN;

	@Option(name="-v",usage="verbose output")
	private boolean verbose;
	
	@Option(name="-config",usage="Dump the exact contents of the node configuration xml file to stdout")
	private boolean config;
    
	protected int run() {
		List<Slave> slaves = Hudson.getInstance().getSlaves();

		if(config)
		{
			try{
				File xml = new File(Hudson.getInstance().getRootDir(),"config.xml");
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		    	DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.parse(xml);
				TransformerFactory tFactory =
				    TransformerFactory.newInstance();
				  Transformer transformer = tFactory.newTransformer();

				NodeList nodeLst = doc.getElementsByTagName("slave");
				for (int s = 0; s < nodeLst.getLength(); s++) {
	
					Node fstNode = nodeLst.item(s);
					
					if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
						Element fstElmnt = (Element) fstNode;
						Element fstNmElmnt = (Element)fstElmnt.getElementsByTagName("name").item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						if(((Node) fstNm.item(0)).getNodeValue().equalsIgnoreCase(nodeName))
						{
							DOMSource source = new DOMSource(fstNode);
							
							StreamResult result = new StreamResult(stdout);
							transformer.transform(source, result);
						}
					}
				}
			}
			catch (Exception e) {
				
			}
		}
		else
		{
			StringBuilder sb = new StringBuilder();
			switch(format)
			{
			
			case CSV :
				sb.append("name,status,noOfExcecutors,Architecture,ClockDiff,FreeDiskSpace,FreeTempSpace,FreeSwapSpace,Label");
				break;
			
			}
			Slave s = Hudson.getInstance().getSlave(nodeName);
	
					
			if(!verbose && format == Format.COLUMN)
			{
//				stdout.println("");
				sb.append("List of Slave");
				sb.append("\n======================================================================");
	
				sb.append("\nStatus\tName\tArchitecture\tClockDiff.\tFreeDiskSpace");
				sb.append("\n======================================================================");
				sb.append("\n");
			}
			
			long avgTime = -1;
			Map<Object,Object> sysProp = new HashMap<Object,Object>();
			
	
				SlaveComputer sc = s.getComputer();
				String status = "",freeTempSpace = "N/A",freeSwapSpace = "N/A",response = "N/A";
				String clockDiff = "N/A",arch = "N/A",freeDiskSpace = "N/A";	
				try
				{
					ClockDifference cd = s.getClockDifference();
					clockDiff = cd.toString();
					//arch = s.getChannel().call(new GetArchTask());
					arch = s.getChannel().call(new NodeService.GetArchTask());
					Computer comp = s.toComputer();
					
	
					NodeService.Data d = NodeService.responceTime(s);
	
					//java.io.File f = new File(System.getProperty("user.home"));
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
					sb.append(status + "\t" + s.getNodeName() + "\t"
							+ arch+"\t"+clockDiff+"\t"+freeDiskSpace);
					//stdout.print(cd.toString()+"\t"+avgTime);
					sb.append("\n");
				}
				else if(verbose)
				{
					sb.append(s.getNodeName());
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
						
						sb.append(s.getNodeName());
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
				stdout.println(sb.toString());
			}
	
		return 0;
	
	}
	
		
}
