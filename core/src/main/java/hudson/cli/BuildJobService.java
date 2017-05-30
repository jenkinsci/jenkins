/**
 * @author aju.balachandran
 */
package hudson.cli;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
/**
 * 
 * @author aju.balachandran
 *@see This service class is singleton.
 */
public class BuildJobService {

	private static BuildJobService instance = null;
	private BuildJobService(){}
	
	public static BuildJobService getInstance()
	{
		if(instance == null)
		{
			instance = new BuildJobService();
		}
		return instance;
	}
	public Map<String, String> getJobBuildDetails(String dirPath,boolean verb)throws Exception
	{
		File dir = new File(dirPath);
		
	    String[] children = dir.list();
	    
	    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	    int successCount = 0, failureCount = 0, lastBuildDuration = 0, lastBuildNumber = 0;
	    String lastSuccessBuildNo = "", lastFailureBuildNo = "";
	    Date successDate = null,tempSuccessDate = null,failureDate = null,tempFailureDate = null;
	    String status = "Pending";
	    String buildDuration = "0";
	    Map<String, String> durationMap = new HashMap<String, String>();
        
	    for(int i = 0; i < children.length; i++)
	    {
	    	String filename = children[i];
	    	String absPath = dirPath + File.separatorChar + filename;
	    	String buildNumber = "",buildResult = "";
            if ((new File(absPath)).isDirectory())
            {
            	File buildXmlFile = new File(absPath+ File.separatorChar +"build.xml");
            	String[] attNames = {"number","result","duration"};
            	
            	Map<String, String> xmlValues = readXmlData(buildXmlFile,attNames);
            	
            	buildNumber = xmlValues.get(attNames[0]);
            	buildResult = xmlValues.get(attNames[1]);
            	if(buildResult.equalsIgnoreCase("SUCCESS"))
            	{
            		buildDuration = xmlValues.get(attNames[2]);
            	}
            	if(i == 0)
            	{
            		status = "Success";
            		lastBuildNumber = Integer.parseInt(buildNumber);
            		lastBuildDuration = Integer.parseInt(buildDuration);
            	}else{
            		if(lastBuildNumber < Integer.parseInt(buildNumber))
            		{
            			lastBuildNumber = Integer.parseInt(buildNumber);
            			lastBuildDuration = Integer.parseInt(buildDuration);
            		}
            	}
            	
            	
            	String datePart = filename.substring(0, filename.indexOf("_"));
            	datePart = datePart.replace("-", "/");
            	String timePart = filename.substring(filename.indexOf("_")+1);
            	timePart = timePart.replace("-", ":");
            	
            	if(buildResult.equalsIgnoreCase("SUCCESS"))
            	{
            		successCount++;
            		successDate = dateFormat.parse(datePart+" "+timePart);
            	}
            	else
            	{
            		failureCount++;
            		failureDate = dateFormat.parse(datePart+" "+timePart);
            	}
            	
            	
            	
            }
            if(successCount == 1)
            {
            	tempSuccessDate = successDate;
            	lastSuccessBuildNo = buildNumber;
            }
            else
            {
	            if(buildResult.equalsIgnoreCase("SUCCESS") && successDate != null && successDate.compareTo(tempSuccessDate)>=0)
	            {
	            	tempSuccessDate = successDate;
	            	
	            }
	            if(buildResult.equalsIgnoreCase("SUCCESS") && Integer.parseInt(lastSuccessBuildNo) < Integer.parseInt(buildNumber))
	            {
	            	lastSuccessBuildNo = buildNumber;
	            }
	            
            }
            if(failureCount == 1)
            {
            	tempFailureDate = failureDate;
            	lastFailureBuildNo = buildNumber;
            }
            else
            {
            	if(!buildResult.equalsIgnoreCase("SUCCESS") && failureDate != null && failureDate.compareTo(tempFailureDate)>=0)
	            {
            		tempFailureDate = failureDate;
	            }
            	if(!buildResult.equalsIgnoreCase("SUCCESS") && Integer.parseInt(lastFailureBuildNo) < Integer.parseInt(buildNumber))
	            {
            		lastFailureBuildNo = buildNumber;
	            }
            }
	    }

	    long bildSuccessTimeMilli = (tempSuccessDate!=null)?tempSuccessDate.getTime():0;
	    long bildFailureTimeMilli = (tempFailureDate!=null)?tempFailureDate.getTime():0;
	    String successDuration = "";
	    if(bildSuccessTimeMilli>0)
	    {
	    	successDuration = getDuration(bildSuccessTimeMilli);
	    }
	    String failureDuration = "";
	    if(bildFailureTimeMilli>0)
	    {
	    	failureDuration = getDuration(bildFailureTimeMilli);	        
	    }
	    if(verb)
		{
    		String[] attArr = {"childProjects"};
    		Map<String, String> verboseDetails = readXmlChildNode(new File(dirPath.substring(0, dirPath.lastIndexOf(File.separatorChar))+ File.separatorChar +"config.xml"),"hudson.tasks.BuildTrigger",attArr);
    		if(verboseDetails.get("childProjects") != null)
    		{
    			durationMap.put("childProjects", verboseDetails.get("childProjects"));
    		}
		}
        durationMap.put("successDuration", successDuration);
        durationMap.put("failureDuration", failureDuration);
        durationMap.put("noOfBuilds", String.valueOf(lastBuildNumber));
        durationMap.put("lastDuration", String.valueOf(lastBuildDuration));
        durationMap.put("lastSuccessBildNo", lastSuccessBuildNo);
        durationMap.put("lastFailureBildNo", lastFailureBuildNo);
        durationMap.put("successCount", String.valueOf(successCount));
        durationMap.put("failureCount", String.valueOf(failureCount));
        
        durationMap.put("status", status);
        
        //Reading config file for getting disabled tag and node name
        String[] configAttNames = {"disabled","assignedNode"};
        Map<String, String> configDetails = readXmlData(new File(dirPath.substring(0, dirPath.lastIndexOf(File.separatorChar))+ File.separatorChar +"config.xml"),configAttNames);
        durationMap.put("disabled", configDetails.get("disabled"));
        durationMap.put("assignedNode", configDetails.get("assignedNode"));
        
        return durationMap;
	}

	public Map<String, String> readXmlData(File xmlPath,String[] attNames)throws Exception
	{
		//System.out.println(xmlPath);
		String retValue = null;
		Map<String, String> retMap = new HashMap<String, String>();
		try {
			  File file = xmlPath;
			  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			  DocumentBuilder db = dbf.newDocumentBuilder();
			  Document doc = db.parse(file);
			  doc.getDocumentElement().normalize();
			  //System.out.println("Root element " + doc.getDocumentElement().getNodeName());
			 
			  Node n = doc.getDocumentElement().getFirstChild();

			  do{
				  n = n.getNextSibling();
				  for(int i = 0; i < attNames.length; i++)
				  {
					  if(n.getNodeName().equals(attNames[i]))
					  {
						  retValue = n.getTextContent();
						  retMap.put(attNames[i], retValue);
						  break;
					  }
				  }
			  }while(n != null );
			  } catch (Exception e) {
			    e.printStackTrace();
			  }
			  
	return retMap;
	}
	
	public String getDuration(long bildTimeMilli)
	{
		long curTimeMilli = System.currentTimeMillis();
	   
	    String duration = "";
	    long durationInSec = (curTimeMilli-bildTimeMilli)/1000;
	    long durationInMin = 0;
	    long durationInHr = 0;
	    long durationInDay = 0;
	    
	    durationInMin = durationInSec/60;
	    durationInHr = durationInMin/60;
	    durationInDay = durationInHr/24;
	    if(durationInDay>0)
	    {
	    	
	    	long hr = durationInHr%24;
	    	duration = durationInDay+" day "+hr+" hr ";
	    }
	    else if(durationInHr>0){
	    	
	    	long min = durationInMin%60;
	    	duration = durationInHr+" hr "+min+" min ";
	    	
	    }
	    else if(durationInMin>0){
	    	
	    	long sec = durationInSec%60;
	    	duration = durationInMin+" min "+sec+" sec ";
	    }
	    else
	    {
	    	duration = durationInSec+" sec ";
	    }
	    	    
        
        return duration;
	}
	
	public Map<String, String> readXmlChildNode(File xmlPath, String parentNode,
			String[] attNames) {
		
		Map<String, String> result = new HashMap<String, String>();
		try {
			
			File file = xmlPath;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();
			
			NodeList nodeLst = doc.getElementsByTagName(parentNode);

			for (int s = 0; s < nodeLst.getLength(); s++) {

				Node fstNode = nodeLst.item(s);

				if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

					Element fstElmnt = (Element) fstNode;
					for (int i = 0; i < attNames.length; i++) {
						NodeList fstNmElmntLst = fstElmnt
								.getElementsByTagName(attNames[i]);
						Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
						NodeList fstNm = fstNmElmnt.getChildNodes();
						result.put(attNames[i], ((Node) fstNm.item(0)).getNodeValue());
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public String findWeather(String weather)
	{
		weather = weather.trim();
		if(weather.equals("1"))
		{
			return "1 - Sunny";
		}
		else if(weather.equals("2"))
		{
			return "2 - Partly Sunny";
		}
		else if(weather.equals("3"))
		{
			return "3 - Cloudy";
		}
		else if(weather.equals("4"))
		{
			return "4 - Rain";
		}
		else if(weather.equals("5"))
		{
			return "5 - Thunder";
		}
		else
		{
			return weather;
		}
	}
	public Date convertToDateFormat(String fileName)throws ParseException
	{
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String datePart = fileName.substring(0, fileName.indexOf("_"));
    	datePart = datePart.replace("-", "/");
    	String timePart = fileName.substring(fileName.indexOf("_")+1);
    	timePart = timePart.replace("-", ":");
    	Date dateWithTime = dateFormat.parse(datePart+" "+timePart);
    	return dateWithTime;
	}
	
	public String[] convertDateToFileNameFormat(List<Date> dateList)throws ParseException
	{
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String fileNames[] = new String[dateList.size()];
		int count = 0; 
		for(Date date : dateList)
		{
			String dateValue = dateFormat.format(date); 
			String datePart = dateValue.substring(0, dateValue.indexOf(" "));
	    	datePart = datePart.replace("/", "-");
	    	String timePart = dateValue.substring(dateValue.indexOf(" ")+1);
	    	timePart = timePart.replace(":", "-");
	    	fileNames[count] = datePart+"_"+timePart;
	    	count++;
//	    	Date dateWithTime = dateFormat.parse(datePart+" "+timePart);
		}
    	return fileNames;
	}
}
