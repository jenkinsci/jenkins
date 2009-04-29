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

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CLI entry point to Hudson.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CLI {
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
        
        if(url==null)
            printUsageAndExit(Messages.CLI_NoURL());

        if(args.isEmpty())
            args = Arrays.asList("help"); // default to help

        FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(url));
        ExecutorService pool = Executors.newCachedThreadPool();
        Channel channel = new Channel("Chunked connection to "+url,
                pool,con.getInputStream(),con.getOutputStream());

        // execute the command
        int r=-1;
        try {
            CliEntryPoint cli = (CliEntryPoint)channel.getRemoteProperty(CliEntryPoint.class.getName());
            if(cli.protocolVersion()!=CliEntryPoint.VERSION) {
                System.err.println(Messages.CLI_VersionMismatch());
            } else {
                r = cli.main(args, Locale.getDefault(), new RemoteInputStream(System.in),
                        new RemoteOutputStream(System.out), new RemoteOutputStream(System.err));
            }
        } finally {
            channel.close();
            pool.shutdown();
        }

        System.exit(r);
    }

    private static void printUsageAndExit(String msg) {
        if(msg!=null)   System.out.println(msg);
        System.err.println(Messages.CLI_Usage());
        System.exit(-1);
    }
}
