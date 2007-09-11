package hudson;

import hudson.util.DualOutputStream;
import hudson.util.EncodingStream;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point to Hudson from command line.
 *
 * <p>
 * This tool runs another process and sends its result to Hudson.
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static int run(String[] args) throws Exception {
        String home = getHudsonHome();
        if(home==null) {
            System.err.println("HUDSON_HOME is not set.");
            return -1;
        }

        return remotePost(args);
    }

    private static String getHudsonHome() {
        return EnvVars.masterEnvVars.get("HUDSON_HOME");
    }

    /**
     * Run command and place the result to a remote Hudson installation
     */
    public static int remotePost(String[] args) throws Exception {
        String projectName = args[0];

        String home = getHudsonHome();
        if(!home.endsWith("/"))     home = home + '/';  // make sure it ends with '/'

        {// check if the home is set correctly
            HttpURLConnection con = (HttpURLConnection)new URL(home).openConnection();
            con.connect();
            if(con.getResponseCode()!=200
            || con.getHeaderField("X-Hudson")==null) {
                System.err.println(home+" is not Hudson ("+con.getResponseMessage()+")");
                return -1;
            }
        }

        String projectNameEnc = URLEncoder.encode(projectName,"UTF-8").replaceAll("\\+","%20");

        {// check if the job name is correct
            HttpURLConnection con = (HttpURLConnection)new URL(home+"job/"+projectNameEnc+"/acceptBuildResult").openConnection();
            con.connect();
            if(con.getResponseCode()!=200) {
                System.err.println(projectName+" is not a valid job name on "+home+" ("+con.getResponseMessage()+")");
                return -1;
            }
        }

        // start a remote connection
        HttpURLConnection con = (HttpURLConnection) new URL(home+"job/"+projectNameEnc+"/postBuildResult").openConnection();
        con.setDoOutput(true);
        con.connect();
        OutputStream os = con.getOutputStream();
        Writer w = new OutputStreamWriter(os,"UTF-8");
        w.write("<?xml version='1.0' encoding='UTF-8'?>");
        w.write("<run><log encoding='hexBinary'>");
        w.flush();

        // run the command
        long start = System.currentTimeMillis();

        List<String> cmd = new ArrayList<String>();
        for( int i=1; i<args.length; i++ )
            cmd.add(args[i]);
        Proc proc = new Proc.LocalProc(cmd.toArray(new String[0]),(String[])null,System.in,
            new DualOutputStream(System.out,new EncodingStream(os)));

        int ret = proc.join();

        w.write("</log><result>"+ret+"</result><duration>"+(System.currentTimeMillis()-start)+"</duration></run>");
        w.close();

        if(con.getResponseCode()!=200) {
            Util.copyStream(con.getErrorStream(),System.err);
        }

        return ret;
    }
}
