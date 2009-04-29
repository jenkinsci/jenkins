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

        URL target = new URL("http://localhost:8080/cli");
        FullDuplexHttpStream con = new FullDuplexHttpStream(target);
        ExecutorService pool = Executors.newCachedThreadPool();
        Channel channel = new Channel("Chunked connection to "+target,
                pool,con.getInputStream(),con.getOutputStream());

        // execute the command
        int r=-1;
        try {
            CliEntryPoint cli = (CliEntryPoint)channel.getRemoteProperty(CliEntryPoint.class.getName());
            if(cli.protocolVersion()!=CliEntryPoint.VERSION) {
                System.err.println("Version mismatch. This CLI cannot work with this Hudson server");
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
}
