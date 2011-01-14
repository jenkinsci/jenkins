/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.cli;

import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.PingThread;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import hudson.cli.client.Messages;

import java.net.URL;
import java.net.URLConnection;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/**
 * CLI entry point to Hudson.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CLI {
    private final ExecutorService pool;
    private final Channel channel;
    private final CliEntryPoint entryPoint;
    private final boolean ownsPool;

    public CLI(URL hudson) throws IOException, InterruptedException {
        this(hudson,null);
    }

    public CLI(URL hudson, ExecutorService exec) throws IOException, InterruptedException {
        String url = hudson.toExternalForm();
        if(!url.endsWith("/"))  url+='/';

        ownsPool = exec==null;
        pool = exec!=null ? exec : Executors.newCachedThreadPool();

        int clip = getCliTcpPort(url);
        if(clip>=0) {
            // connect via CLI port
            String host = new URL(url).getHost();
            LOGGER.fine("Trying to connect directly via TCP/IP to port "+clip+" of "+host);
            Socket s = new Socket(host,clip);
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            dos.writeUTF("Protocol:CLI-connect");

            channel = new Channel("CLI connection to "+hudson, pool,
                    new BufferedInputStream(new SocketInputStream(s)),
                    new BufferedOutputStream(new SocketOutputStream(s)));
        } else {
            // connect via HTTP
            LOGGER.fine("Trying to connect to "+url+" via HTTP");
            url+="cli";
            hudson = new URL(url);

            FullDuplexHttpStream con = new FullDuplexHttpStream(hudson);
            channel = new Channel("Chunked connection to "+hudson,
                    pool,con.getInputStream(),con.getOutputStream());
            new PingThread(channel,30*1000) {
                protected void onDead() {
                    // noop. the point of ping is to keep the connection alive
                    // as most HTTP servers have a rather short read time out
                }
            }.start();
        }

        // execute the command
        entryPoint = (CliEntryPoint)channel.waitForRemoteProperty(CliEntryPoint.class.getName());

        if(entryPoint.protocolVersion()!=CliEntryPoint.VERSION)
            throw new IOException(Messages.CLI_VersionMismatch());
    }

    /**
     * If the server advertises CLI port, returns it.
     */
    private int getCliTcpPort(String url) throws IOException {
        URLConnection head = new URL(url).openConnection();
        try {
            head.connect();
        } catch (IOException e) {
            throw (IOException)new IOException("Failed to connect to "+url).initCause(e);
        }
        String p = head.getHeaderField("X-Hudson-CLI-Port");
        if(p==null) return -1;
        return Integer.parseInt(p);
    }

    public void close() throws IOException, InterruptedException {
        channel.close();
        channel.join();
        if(ownsPool)
            pool.shutdown();
    }

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return entryPoint.main(args,Locale.getDefault(),
                new RemoteInputStream(stdin),
                new RemoteOutputStream(stdout),
                new RemoteOutputStream(stderr));
    }

    public int execute(List<String> args) {
        return execute(args,System.in,System.out,System.err);
    }

    public int execute(String... args) {
        return execute(Arrays.asList(args));
    }

    /**
     * Returns true if the named command exists.
     */
    public boolean hasCommand(String name) {
        return entryPoint.hasCommand(name);
    }

    public static void main(final String[] _args) throws Exception {
        List<String> args = Arrays.asList(_args);

        String url = System.getenv("HUDSON_URL");

        while(!args.isEmpty()) {
            String head = args.get(0);
            if(head.equals("-s") && args.size()>=2) {
                url = args.get(1);
                args = args.subList(2,args.size());
                continue;
            }
            break;
        }
        
        if(url==null) {
            printUsageAndExit(Messages.CLI_NoURL());
            return;
        }

        if(args.isEmpty())
            args = Arrays.asList("help"); // default to help

        CLI cli = new CLI(new URL(url));
        try {
            // execute the command
            // Arrays.asList is not serializable --- see 6835580
            args = new ArrayList<String>(args);
            System.exit(cli.execute(args, System.in, System.out, System.err));
        } finally {
            cli.close();
        }
    }

    private static void printUsageAndExit(String msg) {
        if(msg!=null)   System.out.println(msg);
        System.err.println(Messages.CLI_Usage());
        System.exit(-1);
    }

    private static final Logger LOGGER = Logger.getLogger(CLI.class.getName());
}
