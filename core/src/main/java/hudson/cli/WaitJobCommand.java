/**
 * @author aju.balachandran
 */
package hudson.cli;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import hudson.model.*;
import hudson.Extension;
import hudson.views.*;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import hudson.cli.commons.DateComparatorDesc;


@Extension
public class WaitJobCommand  extends CLICommand{

	public String getShortDescription() {
        return "Shows Waiting Hudson Job Build";
    }
	
	@Argument(required = true,metaVar="JOBNAME",usage="Name of the Job",index=0)
	public String jobName;
	
	@Option(name="-n",metaVar="BUILD NUMBER",usage="Build Number")
	public String buildNo;
	
	BuildJobService buildService = BuildJobService.getInstance();
	protected int run() {
		
		ReadXmlData readXmlData = new ReadXmlData();
		String build = "";
		boolean flag = false;
		stdout.print("Waiting");
		
		do
		{
			stdout.print(".");	
			File jobBuildFolder = new File(new File(System.getProperty("user.home")),".hudson\\jobs\\"+jobName+"\\builds");
			ArrayList<File> fileList = new ArrayList<File>();
			String[] attNames = {"number"};
			Map<String, String> buildNoMap = new HashMap<String, String>();
			try
			{
				flag = checkThisJobBuildCompleted(jobBuildFolder.getAbsolutePath(),"build.xml");

				if(!flag)
				{
//					Thread.sleep(6000);//6 seconds
					Thread.sleep(60000);//60 seconds
				}
				else
				{
					stdout.print("\n");
					stdout.println("Completed.");
				}
			}catch (Exception e) {
				System.out.println(e.toString());
			}
			
		}while(!flag );
	return 0;	
	}
	
	public boolean checkThisJobBuildCompleted(String dirName,String fileName)throws Exception
	{
		
		File dir = new File(dirName);
	   
	    String[] children = dir.list();
	    
	    List<Date> dateList = new ArrayList<Date>();
	    
	    for(int i = 0; i < children.length; i++)
	    {
	    	dateList.add(buildService.convertToDateFormat(children[i]));
	    }
	    //DateComparator dc = new DateComparator();
	    DateComparatorDesc dcd = new DateComparatorDesc();
	    Collections.sort(dateList, dcd);
	    String fileNames[] = buildService.convertDateToFileNameFormat(dateList);
	    boolean flag = true;
	    String filename = "";
	    if(buildNo != null && !buildNo.trim().equalsIgnoreCase(""))
	    {	
	    	
	    	for(int i = fileNames.length-1; i >= 0 ; i--)
    	    {
    	    	filename = fileNames[i];
    	    	if ((new File(dirName + File.separatorChar + filename)).isDirectory()) {
              	
    	    		flag = checkBuildCompleted(dirName + File.separatorChar + filename,fileName,buildNo);
    	    		if(flag)
    	    		{
    	    			break;
    	    		}
    	    	}
    	    }
	    }
	    else
	    {
	    	filename = fileNames[0];
	    	if ((new File(dirName + File.separatorChar + filename)).isDirectory()) {
	          	
	    		flag = checkBuildCompleted(dirName + File.separatorChar + filename,fileName);
	    	
	    	}
	    }
	    

	    return flag;
	}
	
	public boolean checkBuildCompleted(String dirName,String fileName)throws Exception
	{
		File dir = new File(dirName);
		   
	    String[] children = dir.list();
	    boolean flag = false;
	    for(int i = 0; i < children.length; i++)
	    {
	    	String filename = children[i];
	    	
	    	if ((new File(dirName + File.separatorChar + filename)).isDirectory()) {
            	
            	
            }
	    	else
	    	{
	    		if(filename.equalsIgnoreCase(fileName))
            	{
            		flag = true;
            		break;
            	}
	    	}
	    }
	    return flag;
	}
	
	public boolean checkBuildCompleted(String dirName,String fileName,String buildNo)throws Exception
	{
		File dir = new File(dirName);
		   
	    String[] children = dir.list();
	    boolean flag = false;
	    for(int i = 0; i < children.length; i++)
	    {
	    	String filename = children[i];
	    	
	    	if ((new File(dirName + File.separatorChar + filename)).isDirectory()) {
            	
            	
            }
	    	else
	    	{
	    		if(filename.equalsIgnoreCase(fileName))
            	{
	    			String buildXmlFile = dirName + File.separatorChar + filename;
	    			String[] attNames = {"number"};
	            	
	            	Map<String, String> xmlValues = buildService.readXmlData(new File(buildXmlFile),attNames);
	            	if(buildNo.equals(xmlValues.get("number")))
	            	{
	            		flag = true;
	            		break;
	            	}
            	}
	    	}
	    }
	    return flag;
	}
	
}
