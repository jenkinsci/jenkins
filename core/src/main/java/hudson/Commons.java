package hudson;
/**
 * @author aju.balachandran
 */
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.StringTokenizer;

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
}
