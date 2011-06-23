package org.jenkinsci.plugins.cli.common;
/**
 * @author aju.balachandran
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class Commons {

	public static final String nodeOffLineStatus = "offline";
	public static final String nodeOnLineStatus = "online";
	
	public String[] splitString(String strValue,String separator)
	{
		int c = 0,count = 0;
		StringTokenizer st = new StringTokenizer (strValue,separator);
		while (st.hasMoreTokens ()) {
			st.nextToken ();
			c++;
		}
		String arr[] = new String[c];
		StringTokenizer str = new StringTokenizer (strValue,separator);
		while (str.hasMoreTokens ()) {
			arr[count] = str.nextToken ();
			count++;
		}
		return arr; 
	}
	
	public static double roundOneDecimals(double d) {
    	DecimalFormat oneDForm = new DecimalFormat("#.#");
    	return Double.valueOf(oneDForm.format(d));
	}
	public static double roundTwoDecimals(double d) {
    	DecimalFormat twoDForm = new DecimalFormat("#.##");
    	return Double.valueOf(twoDForm.format(d));
	}

	public static double setToOneDecimal(double d)
	{
		String s = String.valueOf(d);
		s = s.substring(0, s.indexOf(".")+2);
		return Double.valueOf(s);
	}
	
	public Map<String, String> readXmlData(File xmlPath,String[] attNames)
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
			  }while(n != null && retMap.size()< attNames.length);
			  } catch (Exception e) {
			    e.printStackTrace();
			  }
			  
	return retMap;
	}
	public File getLogFileForBuild(File buildDir,int buildNum)
	{
		File logFile = null;
		String[] children = buildDir.list();
	    
//	    TreeSet<Date> dateSet = new TreeSet<Date>();
		ArrayList<Date> dateList = new ArrayList<Date>();
	    
	    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	    /**
	     * Sorting Build folders for a Job, Folder name is date of build generation.  
	     */
	    for(int i = 0; i < children.length; i++)
	    {
	    	String filename = children[i];
	    	String datePart = filename.substring(0, filename.indexOf("_"));
        	datePart = datePart.replace("-", "/");
        	String timePart = filename.substring(filename.indexOf("_")+1);
        	timePart = timePart.replace("-", ":");
        	
        	try
        	{
//        		dateSet.add(dateFormat.parse(datePart+" "+timePart));
        		dateList.add(dateFormat.parse(datePart+" "+timePart));
        	}catch (Exception e) {
				System.out.println(e.toString());
			}
	    }
	    /**
	     * Sorting list in revers order.
	     */
	    Comparator<Date> comp = Collections.reverseOrder();
	    Collections.sort(dateList, comp);
	    for(Date date : dateList)
	    {
    		String filename = dateFormat.format(date);
    		filename = filename.replace(":", "-");
    		filename = filename.replace("/", "-");
    		filename = filename.replace(" ", "_");
    		File dateWiseBuild = new File(buildDir, filename);
//    		String absPath = buildDir.getAbsolutePath() + File.separatorChar + filename;
            if (dateWiseBuild.isDirectory())
            {
            	File buildXmlFile = new File(dateWiseBuild,"build.xml");
            	String[] attNames = {"number"};
            	
            	Map<String, String> xmlValues = readXmlData(buildXmlFile,attNames);
            	if(buildNum == Integer.parseInt(xmlValues.get(attNames[0])))
            	{
            		logFile = new File(dateWiseBuild, "log");
            		break;
            	}
            }
	    }
	    return logFile;
	}
	public String getLogInfo(File logFile)
	{
		String value = "";
		BufferedReader br = null;
		try
		{
			String arr[] = new String[10];
			br = new BufferedReader(new FileReader(logFile));
			String line = null;
			
			if (( line = br.readLine()) != null){
				arr = splitString(line," ");
		    }
			
			br.close();
			
			if(arr[3].trim().equalsIgnoreCase("project"))
			{
				
				value = arr[4].substring(arr[4].lastIndexOf("[0m")+3, arr[4].length()-1);
				String buildNo = arr[7].substring(arr[7].lastIndexOf("[0m")+3);
				String svnUrl = "";
				value = value + " & #"+buildNo;
//				ByteBuffer bb = ByteBuffer.wrap(arr[4].getBytes());
//				value = decoder.decode(bb).toString();
				
			    
			}
			else if(line.contains("SCM"))
			{
				value = "SCM";
			}
			else
			{
				value = arr[3];
			}
		}catch (Exception e) {
			System.out.println(e.toString());
		}
		finally{
			try
			{
				br.close();
			}catch (IOException ioe) {
				System.out.println(ioe.toString());
			}
			
		}
		return value;
	}
}
