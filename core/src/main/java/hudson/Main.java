/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import hudson.util.DualOutputStream;
import hudson.util.EncodingStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.Charset;

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

        // write the output to a temporary file first.
        File tmpFile = File.createTempFile("hudson","log");
        tmpFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(tmpFile);

        Writer w = new OutputStreamWriter(os,"UTF-8");
        w.write("<?xml version='1.0' encoding='UTF-8'?>");
        w.write("<run><log encoding='hexBinary' content-encoding='"+Charset.defaultCharset().name()+"'>");
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

        String location = home+"job/"+projectNameEnc+"/postBuildResult";
        while(true) {
            try {
                // start a remote connection
                HttpURLConnection con = (HttpURLConnection) new URL(location).openConnection();
                con.setDoOutput(true);
                // this tells HttpURLConnection not to buffer the whole thing
                con.setFixedLengthStreamingMode((int)tmpFile.length());
                con.connect();
                // send the data
                FileInputStream in = new FileInputStream(tmpFile);
                Util.copyStream(in,con.getOutputStream());
                in.close();

                if(con.getResponseCode()!=200) {
                    Util.copyStream(con.getErrorStream(),System.err);
                }

                return ret;
            } catch (HttpRetryException e) {
                if(e.getLocation()!=null) {
                    // retry with the new location
                    location = e.getLocation();
                    continue;
                }
                // otherwise failed for reasons beyond us.
                throw e;
            }
        }
    }
}
