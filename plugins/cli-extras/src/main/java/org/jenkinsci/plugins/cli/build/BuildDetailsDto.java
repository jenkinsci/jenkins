package org.jenkinsci.plugins.cli.build;

import java.io.Serializable;
import org.jenkinsci.plugins.cli.common.Commons;

public class BuildDetailsDto implements Serializable{

	private int buildNumber;
	private String buildResult;
	private String buildDuration;
	private String slaveName;
	private String buildDateTime;
	private String jobName;
	private String trigger;
	
	
	public int getBuildNumber()
	{
		return buildNumber;
	}
	public void setBuildNumber(int buildNumber)
	{
		this.buildNumber = buildNumber;
	}
	public String getBuildResult()
	{
		return buildResult;
	}
	public void setBuildResult(String buildResult)
	{
		this.buildResult = buildResult;
	}
	public String getBuildDuration()
	{
		double hr,min,sec,value = 0.0;
		boolean flag = true;
		try
		{
			value = Double.parseDouble(buildDuration);
		}catch (NumberFormatException nfe) {
			flag = false;
		}
		if(flag && value>=3600000)
		{
			hr = value/3600000.0;
			buildDuration = Commons.roundOneDecimals(hr)+" hr";
		}
		else if(flag && value>=60000)
		{
			min = value/60000.0;
			buildDuration = Commons.roundOneDecimals(min) + " min";
		}
		else if(flag && value>=1000)
		{
			sec = value/1000.0;
			buildDuration = Commons.setToOneDecimal(sec) + " sec";
		}
		else
		{
			buildDuration = buildDuration+" ms";
		}
		
		return buildDuration;
	}
	public void setBuildDuration(String buildDuration)
	{
		this.buildDuration = buildDuration;
	}
	public String getSlaveName()
	{
		return slaveName;
	}
	public void setSlaveName(String slaveName)
	{
		this.slaveName = slaveName;
	}
	public String getBuildDateTime()
	{
		return buildDateTime;
	}
	public void setBuildDateTime(String buildDateTime)
	{
		this.buildDateTime = buildDateTime;
	}
	public String getJobName()
	{
		return jobName;
	}
	public void setJobName(String jobName)
	{
		this.jobName = jobName;
	}
	public String getTrigger()
	{
		return trigger;
	}
	public void setTrigger(String trigger)
	{
		this.trigger = trigger;
	}
}
