package org.jenkinsci.plugins.cli.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import hudson.cli.CLICommand;
import hudson.model.Hudson;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.model.Node.Mode;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SimpleScheduledRetentionStrategy;
import hudson.slaves.CommandLauncher;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.IOUtils;
import hudson.os.windows.ManagedWindowsServiceLauncher;
import hudson.plugins.sshslaves.SSHLauncher;

import org.kohsuke.args4j.Argument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Extension
public class CreateNodeCommand extends CLICommand{

	
	@Override
    public String getShortDescription() {
        return "Creates a new node by reading stdin as a configuration XML file";
    }

    @Argument(required = true,metaVar="NAME",usage="Name of the node to create")
    public String name;
    
    protected int run() throws Exception {
        
	   	
	   	createNodeFromXML(name,stdin);
        
        return 0;
    }

    public void createNodeFromXML(String name,InputStream xml)throws Exception
    {
//    	stdout.println(xml);
//    	StringWriter writer = new StringWriter();
//    	IOUtils.copy(xml, writer);
//    	String theString = writer.toString(); 
//    	stdout.println(theString);
    	
    	String numExecutors = "2";
	   	String remoteFS = "";
    	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    	DocumentBuilder db = dbf.newDocumentBuilder();
   
    	Document doc = db.parse((InputStream)xml);
    	
//    	stdout.println("Root element " + doc.getDocumentElement().getNodeName());
    	NodeList nodeLst = doc.getElementsByTagName("slave");
//    	stdout.println("Information of all Slave");

    	  String attList[] = {"remoteFS","numExecutors","mode","retentionStrategy","launcher","label","nodeProperties"};
    	  String outList[] = new String[7];
    	
    	  ComputerLauncher computerLauncher = (ComputerLauncher)new JNLPLauncher();
    	  RetentionStrategy retentionStrategy = (RetentionStrategy)RetentionStrategy.Always.INSTANCE;
    	  List nodeProperties = new ArrayList();
    	 
    	  for (int s = 0; s < nodeLst.getLength(); s++) {

    	    Node fstNode = nodeLst.item(s);
    	    
    	    if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
    	  
    	    	for(int i = 0; i<attList.length;i++)
    	    	{
    	    		  Element fstElmnt = (Element) fstNode;
    	    	      NodeList fstNmElmntLst = fstElmnt.getElementsByTagName(attList[i]);
    	    	      Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
    	    	      if(i == 3 || i == 4)
    	    	      {
    	    	    	  outList[i] = fstNmElmnt.getAttribute("class");
    	    	    	  if(attList[i].equalsIgnoreCase("retentionStrategy"))
    	    	    	  {
    	    	    		  if(outList[3].endsWith("RetentionStrategy$Always"))
    	    	    		  {
    	    	    			  retentionStrategy = RetentionStrategy.Always.INSTANCE;
    	    	    		  }
    	    	    		  else if(outList[3].endsWith("RetentionStrategy$Demand"))
    	    	    		  {
    	    	    			  long inDemandDelay = 0l;
    	    	    			  long idleDelay = 0l;
    	    	    			  NodeList fstNm = fstNmElmnt.getChildNodes();
    	    	    			  for(int l = 0; l < fstNm.getLength(); l++)
    	    	    			  {
//    	    	    				  stdout.println(((Node) fstNm.item(l)).getNodeName()+" : "+((Node) fstNm.item(l)).getTextContent());
    	    	    				  if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("inDemandDelay"))
    	    	    				  {
    	    	    					  inDemandDelay = Long.parseLong(((Node) fstNm.item(l)).getTextContent());
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("idleDelay"))
    	    	    				  {
    	    	    					  idleDelay = Long.parseLong(((Node) fstNm.item(l)).getTextContent());
    	    	    				  }
    	    	    				  
    	    	    			  }
    	    	    			  retentionStrategy =  new RetentionStrategy.Demand(inDemandDelay,idleDelay);
    	    	    		  }
    	    	    		  else if(outList[3].endsWith("SimpleScheduledRetentionStrategy"))
    	    	    		  {
    	    	    			  String startTimeSpec = "";
    	    	    			  int upTimeMins = 0;
    	    	    			  boolean keepUpWhenActive = true;
    	    	    			  NodeList fstNm = fstNmElmnt.getChildNodes();
    	    	    			  for(int l = 0; l < fstNm.getLength(); l++)
    	    	    			  {
//    	    	    				  stdout.println(((Node) fstNm.item(l)).getNodeName()+" : "+((Node) fstNm.item(l)).getTextContent());
    	    	    				  if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("startTimeSpec"))
    	    	    				  {
    	    	    					  startTimeSpec = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("upTimeMins"))
    	    	    				  {
    	    	    					  upTimeMins = Integer.parseInt(((Node) fstNm.item(l)).getTextContent());
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("keepUpWhenActive"))
    	    	    				  {
    	    	    					  keepUpWhenActive = Boolean.parseBoolean(((Node) fstNm.item(l)).getTextContent());
    	    	    				  }
    	    	    			  }
    	    	    			  retentionStrategy =  new SimpleScheduledRetentionStrategy(startTimeSpec,upTimeMins,keepUpWhenActive);
    	    	    		  }
    	    	    	  }
    	    	    	  else if(attList[i].equalsIgnoreCase("launcher"))
    	    	    	  {
    	    	    		  if(outList[4].endsWith("JNLPLauncher"))
    	    	    		  {
    	    	    			  computerLauncher = new JNLPLauncher();
    	    	    		  }
    	    	    		  else if(outList[4].endsWith("SSHLauncher"))
    	    	    		  {
    	    	    			  String host = "";
    	    	    			  int port = 0;
    	    	    			  String userName = "";
    	    	    			  String password = "";
    	    	    			  String privateKey = "";
    	    	    			  String jvmOptions = "";
    	    	    			  String javaPath = "";
    	    	    			  NodeList fstNm = fstNmElmnt.getChildNodes();
    	    	    			  for(int l = 0; l < fstNm.getLength(); l++)
    	    	    			  {
//    	    	    				  stdout.println(((Node) fstNm.item(l)).getNodeName()+" : "+((Node) fstNm.item(l)).getTextContent());
    	    	    				  if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("host"))
    	    	    				  {
    	    	    					  host = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("port"))
    	    	    				  {
    	    	    					  port = Integer.parseInt(((Node) fstNm.item(l)).getTextContent());
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("username"))
    	    	    				  {
    	    	    					  userName = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("password"))
    	    	    				  {
    	    	    					  password = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("privatekey"))
    	    	    				  {
    	    	    					  privateKey = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("jvmOptions"))
    	    	    				  {
    	    	    					  jvmOptions = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("javaPath"))
    	    	    				  {
    	    	    					  javaPath = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    			  }
//    	    	    			  computerLauncher = new SSHLauncher(host,port,userName,password,privateKey,jvmOptions);
    	    	    			  computerLauncher = new SSHLauncher(host,port,userName,password,privateKey,jvmOptions,javaPath);
    	    	    		  }
    	    	    		  else if(outList[4].endsWith("ManagedWindowsServiceLauncher"))
    	    	    		  {
    	    	    			  String userName = "";
    	    	    			  String password = "";
    	    	    			  NodeList fstNm = fstNmElmnt.getChildNodes();
    	    	    			  for(int l = 0; l < fstNm.getLength(); l++)
    	    	    			  {
//    	    	    				  stdout.println(((Node) fstNm.item(l)).getNodeName()+" : "+((Node) fstNm.item(l)).getTextContent());
    	    	    				  if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("username"))
    	    	    				  {
    	    	    					  userName = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  }
    	    	    				  else if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("password"))
    	    	    				  {
    	    	    					  password = ((Node) fstNm.item(l)).getTextContent();
    	    	    				  } 
    	    	    			  }
    	    	    			  computerLauncher = new ManagedWindowsServiceLauncher(userName,password);
    	    	    		  }
    	    	    		  else if(outList[4].endsWith("CommandLauncher"))
    	    	    		  {
    	    	    			  String agentCommand = "";
    	    	    			  NodeList fstNm = fstNmElmnt.getChildNodes();
    	    	    			  for(int l = 0; l < fstNm.getLength(); l++)
    	    	    			  {
	    	    	    			  if(((Node) fstNm.item(l)).getNodeName().equalsIgnoreCase("agentCommand"))
		    	    				  {
	    	    	    				  agentCommand = ((Node) fstNm.item(l)).getTextContent();
		    	    				  }
    	    	    			  }
    	    	    			  computerLauncher = new CommandLauncher(agentCommand);
    	    	    		  }
    	    	    	  }
    	    	    	  
    	    	      }else if(i == 6)
	    	    	  {
    	    	    	  String key = "";
    	    	    	  String value = "";
    	    	    	  EnvironmentVariablesNodeProperty envVarNodeProp = null;
    	    	    	  if(fstNmElmnt.getNodeName().equalsIgnoreCase("nodeProperties"))
    	    	    	  {
    	    	    		  NodeList node = doc.getElementsByTagName("envVars");
    	    	    		  for (int j = 0; j < node.getLength(); j++) {
    	    	        		  Node firstNode = node.item(j);
    	    	        		    
    	    	        		  if (firstNode.getNodeType() == Node.ELEMENT_NODE) {
    	    	        		  
	    	    	        		  Element element = (Element) firstNode;
	    	    	        		  NodeList firstNameElemntList = element.getElementsByTagName("string");
	    	    	        		  List<EnvironmentVariablesNodeProperty.Entry> envList = new ArrayList<EnvironmentVariablesNodeProperty.Entry>();
	    	    	        		  for(int k = 0; k < firstNameElemntList.getLength(); k++)
	    	    	        		  {
		    	    	        		  Element firstNameElement = (Element) firstNameElemntList.item(k);
		    	    	        		  NodeList firstName = firstNameElement.getChildNodes();
		    	    	        		  if(k%2 != 1)
		    	    	        		  {
		    	    	        			  key = ((Node)firstName.item(0)).getNodeValue();
//		    	    	        			  stdout.println("key :"+ ((Node)firstName.item(0)).getNodeValue());
		    	    	        		  }else
		    	    	        		  {
		    	    	        			  value = ((Node)firstName.item(0)).getNodeValue();
//		    	    	        			  stdout.println("value :"+ ((Node)firstName.item(0)).getNodeValue());
		    	    	        			  EnvironmentVariablesNodeProperty.Entry entity = new EnvironmentVariablesNodeProperty.Entry(key,value);
		    	    	        			  
		    	    	        			  envList.add(entity);
		    	    	        			 
		    	    	        		  }
	    	    	        		  }
	    	    	        		  envVarNodeProp = new EnvironmentVariablesNodeProperty(envList);
    	    	        		  }
    	    	    		  }
    	    	    		  if(envVarNodeProp != null)
    	    	    		  {
    	    	    			  nodeProperties.add(envVarNodeProp);
    	    	    		  }
    	    	    	  }
    	    	    	  
	    	    	  }else
    	    	      {
	    	    	      NodeList fstNm = fstNmElmnt.getChildNodes();
	    	    	      outList[i] = ((Node) fstNm.item(0)).getNodeValue();
//	    	    	      stdout.println(attList[i]+" : "  + outList[i]);
    	    	      }
    	    	}

    	    }

    	  }

    	
    	Hudson h = Hudson.getInstance();
    	Slave s = null;
    	if(nodeProperties != null && !nodeProperties.isEmpty())
    	{
    		s = new DumbSlave(name, "",outList[0],outList[1],outList[2].equalsIgnoreCase("normal")?Mode.NORMAL:Mode.EXCLUSIVE,outList[5],computerLauncher, retentionStrategy,nodeProperties);
    	}
    	else
    	{
    		s = new DumbSlave(name, "",outList[0],outList[1],outList[2].equalsIgnoreCase("normal")?Mode.NORMAL:Mode.EXCLUSIVE,outList[5],computerLauncher, retentionStrategy);
    	}
	     ArrayList<Slave> slaveList = new ArrayList<Slave>(Hudson.getInstance().getSlaves());
	     slaveList.add(s);
	     h.setSlaves(slaveList);
	     stdout.println("Slave creation successfully completed");

    }
}
