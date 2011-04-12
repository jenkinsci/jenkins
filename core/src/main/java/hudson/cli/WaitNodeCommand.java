/**
 * @author aju.balachandran
 */
package hudson.cli;

import hudson.Commons;
import hudson.model.*;
import hudson.Extension;
import hudson.views.*;
import org.kohsuke.args4j.Argument;

@Extension
public class WaitNodeCommand extends CLICommand{

	public String getShortDescription() {
        return "Shows Waiting Node status";
    }
	
	@Argument(required = true,metaVar="Node Name",usage="Name of the Node",index=0)
	public String nodeName;
	
	@Argument(required = true,metaVar="Node Status",usage="online/offline",index=1)
	public String nodeStatus;
		
	protected int run() {
		
		stdout.print("Waiting");
		boolean b = false;		
		do{
			stdout.print(".");
			Computer c1 = Hudson.getInstance().getComputer(nodeName);
			if(nodeStatus.equalsIgnoreCase(Commons.nodeOnLineStatus))
			{
				b = c1.isOnline();
			}else if(nodeStatus.equalsIgnoreCase(Commons.nodeOffLineStatus))
			{
				b = c1.isOffline();
			}
			else
			{
				stdout.print("\n Invalid Node Status, status is either '"+Commons.nodeOffLineStatus+"' or '"+Commons.nodeOnLineStatus+"'");
				break;
			}
			if(b)
			{
				stdout.print("\n");
				stdout.print("Completed.");
				break;
			}
			else
			{
				try
				{
					//Thread.sleep(6000);
					Thread.sleep(60000);//60 seconds
				}catch (Exception e) {
					
				}
			}
		}while(true);
		
		return 0;
	}
}
