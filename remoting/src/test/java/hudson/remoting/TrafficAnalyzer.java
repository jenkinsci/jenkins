package hudson.remoting;

import hudson.remoting.RemoteInvocationHandler.RPCRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;

/**
 * A little forensic analysis tool to figure out what information master and slaves are exchanging.
 *
 * <p>
 * Use the tee command or network packet capturing tool to capture the traffic between the master and
 * the slave, then run it through this tool to get the dump of what commands are sent between them.
 *
 * @author Kohsuke Kawaguchi
 */
public class TrafficAnalyzer {
    public static void main(String[] args) throws Exception {
        File f = new File("/home/kohsuke/ws/hudson/investigations/javafx-windows-hang/out.log");
        DataInputStream fin = new DataInputStream(new FileInputStream(f));
        fin.readFully(new byte[4]); // skip preamble
        ObjectInputStream ois = new ObjectInputStream(fin);
        for (int n=0; ; n++) {
            Command o = (Command)ois.readObject();
            System.out.println("#"+n+" : "+o);
            if (o instanceof RPCRequest) {
                RPCRequest request = (RPCRequest) o;
                System.out.print("  (");
                boolean first=true;
                for (Object argument : request.getArguments()) {
                    if(first)   first=false;
                    else        System.out.print(",");
                    System.out.print(argument);
                }
                System.out.println(")");
            }
            if (o.createdAt!=null)
                o.createdAt.printStackTrace(System.out);
        }
    }
}
