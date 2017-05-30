/**
 * 
 */
package org.jenkinsci.plugins.cli.node;

/**
 * @author aju.balachandran
 *
 */
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hudson.remoting.VirtualChannel;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.FilePath.FileCallable;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import hudson.slaves.OfflineCause;
import hudson.util.TimeUnit2;
import hudson.util.IOException2;

//import org.jvnet.animal_sniffer.IgnoreJRERequirement;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.sun.management.OperatingSystemMXBean;

class NodeService {

	private static NodeService instance = null;
	
	public NodeService(){}
	
	public static NodeService getInstance()
	{
		if(instance == null)
		{
			instance = new NodeService();
		}
		return instance;
	}
	public static String getArchMaster()
	{
		String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        return os+" ("+arch+')';
	}
	static class GetArchTask implements Callable<String,RuntimeException> {
        public synchronized String call() {
            String os = System.getProperty("os.name");
            String arch = System.getProperty("os.arch");
            return os+" ("+arch+')';
        }
       
    }
	static class GetUsableSpace implements FileCallable<String> {
       // @IgnoreJRERequirement
        public synchronized String invoke(File f, VirtualChannel channel) throws IOException {
        	String freeSpace = "";
            try {
                long space = f.getUsableSpace();
                long spaceInMB = space/(1024*1024);
	   	        long spaceInGB = 0l; 
	   	        if(spaceInMB>=1024)
	   	        {
	   	        	spaceInGB = spaceInMB/1024;
	   	        	freeSpace = spaceInGB+" GB";
	   	        }
	   	        else
	   	        {
	   	        	freeSpace = spaceInMB+" MB";
	   	        }
            } catch (LinkageError e) {
                // pre-mustang
                return freeSpace;
            }
            return freeSpace;
        }
   }
	static class GetSwapSpace implements Callable<String,RuntimeException> {
		 public synchronized String call() {
			 String freeSwapSpace = "";
			 OperatingSystemMXBean mxbean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			 
	         long space = mxbean.getFreeSwapSpaceSize();
	         long spaceInMB = space/(1024*1024);
	         long spaceInGB = 0l; 
        	 freeSwapSpace = spaceInMB+" MB";
			 return freeSwapSpace;
	     }
   }
	
	static class GetTempSpace implements FileCallable<String> {
		//@IgnoreJRERequirement
        public synchronized String invoke(File f, VirtualChannel channel) throws IOException {
			String freeTempSpace = "";
            try {
            	f = new File(System.getProperty("user.home"));//java.io.tmpdir
            	long space = f.getUsableSpace();
	   	        long spaceInMB = space/(1024*1024);
	   	        long spaceInGB = 0l; 
	   	        if(spaceInMB>=1024)
	   	        {
	   	        	spaceInGB = spaceInMB/1024;
	   	        	freeTempSpace = spaceInGB+" GB";
	   	        }
	   	        else
	   	        {
	   	        	freeTempSpace = spaceInMB+" MB";
	   	        }
	   			
                return freeTempSpace;
            } catch (LinkageError e) {
                // pre-mustang
                return freeTempSpace;
            }
        }
	}
	public static synchronized Data responceTime(Slave s)throws IOException2,InterruptedException,IOException
	{
		Data old = null;
        Data d;
        Computer comp = s.toComputer();
        long start = System.nanoTime();
        Future<String> f = comp.getChannel().callAsync(new NoopTask());
        try {
            f.get(TIMEOUT, TimeUnit.MILLISECONDS);
            long end = System.nanoTime();
            d = new Data(old,TimeUnit2.NANOSECONDS.toMillis(end-start));
        } catch (ExecutionException e) {
            throw new IOException2(e.getCause());    // I don't think this is possible
        } catch (TimeoutException e) {
            // special constant to indicate that the processing timed out.
            d = new Data(old,-1L);
        }
        return d;
	}
	
	static class NoopTask implements Callable<String,RuntimeException> {
        public String call() {
            return null;
        }
	}
	 @ExportedBean
	 public static final class Data extends OfflineCause {
	        /**
	         * Record of the past 5 times. -1 if time out. Otherwise in milliseconds.
	         * Old ones first.
	         */
	        private final long[] past5;

	        private Data(Data old, long newDataPoint) {
	            if(old==null)
	                past5 = new long[] {newDataPoint};
	            else {
	                past5 = new long[Math.min(5,old.past5.length+1)];
	                int copyLen = past5.length - 1;
	                System.arraycopy(old.past5, old.past5.length-copyLen, this.past5, 0, copyLen);
	                past5[past5.length-1] = newDataPoint;
	            }
	        }

	        /**
	         * Computes the recurrence of the time out
	         */
	        private int failureCount() {
	            int cnt=0;
	            for(int i=past5.length-1; i>=0 && past5[i]<0; i--, cnt++)
	                ;
	            return cnt;
	        }

	        /**
	         * Computes the average response time, by taking the time out into account.
	         */
	        @Exported
	        public long getAverage() {
	            long total=0;
	            for (long l : past5) {
	                if(l<0)     total += TIMEOUT;
	                else        total += l;
	            }
	            return total/past5.length;
	        }

	        public boolean hasTooManyTimeouts() {
	            return failureCount()>=5;
	        }

	        public String toString() {

	            return getAverage()+"ms";
	        }
	    }
	    /**
	     * Time out interval in milliseconds.
	     */
	    private static final long TIMEOUT = 5000;
	    
	    public static String getSpeceConversion(long freeSpaceInByte)
	    {
	    	String freeSpace = "";
	    	long spaceInMB = freeSpaceInByte/(1024*1024);
			long spaceInGB = 0l; 
	        if(spaceInMB>=1024)
	        {
	        	spaceInGB = spaceInMB/1024;
	        	freeSpace = spaceInGB+" GB";
	        }
	        else
	        {
	        	freeSpace = spaceInMB+" MB";
	        }
	        return freeSpace;
	    }
	    
	    public static Data responceTimeOfMaster(Computer comp)throws IOException2,InterruptedException,IOException
	    {
//	    	Computer c[] = Hudson.getInstance().getComputers();
	    	Data old = null;
	    	Data d;
//	        Computer comp = c[0];
	        long start = System.nanoTime();
	        Future<String> f = comp.getChannel().callAsync(new NoopTask());
	        try {
	            f.get(TIMEOUT, TimeUnit.MILLISECONDS);
	            long end = System.nanoTime();
	            d = new Data(old,TimeUnit2.NANOSECONDS.toMillis(end-start));
	        } catch (ExecutionException e) {
	            throw new IOException2(e.getCause());    // I don't think this is possible
	        } catch (TimeoutException e) {
	            // special constant to indicate that the processing timed out.
	            d = new Data(old,-1L);
	        }
	        return d;
	    }
}
