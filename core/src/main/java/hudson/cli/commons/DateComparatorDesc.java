package hudson.cli.commons;

import java.util.Comparator;
import java.util.Date;

public class DateComparatorDesc implements Comparator<Date>{
	
	public int compare(Date first,Date second)
	{
		if(first.compareTo(second)>0)
		{
			return 0;
		}
		else
		{
			return 1;
		}
	}

}
