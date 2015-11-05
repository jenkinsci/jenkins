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

import hudson.remoting.CallableFilter;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * {@link CliEntryPoint} implementation exposed to the remote CLI.
 *
 * @author Kohsuke Kawaguchi
 */
public class CliManagerImpl implements CliEntryPoint, Serializable {
    private transient final Channel channel;
    
    private Authentication transportAuth;

    /**
     * Runs callable from this CLI client with the transport authentication credential.
     */
    private final CallableFilter authenticationFilter = new CallableFilter() {
        public <V> V call(Callable<V> callable) throws Exception {
            SecurityContext context = SecurityContextHolder.getContext();
            Authentication old = context.getAuthentication();
            if (transportAuth!=null)
                context.setAuthentication(transportAuth);
            try {
                return callable.call();
            } finally {
                context.setAuthentication(old);
            }
        }
    };

    public CliManagerImpl(Channel channel) {
        this.channel = channel;
        channel.addLocalExecutionInterceptor(authenticationFilter);
    }

    public int main(List<String> args, Locale locale, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        // remoting sets the context classloader to the RemoteClassLoader,
        // which slows down the classloading. we don't load anything from CLI,
        // so counter that effect.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        PrintStream out = new PrintStream(stdout);
        PrintStream err = new PrintStream(stderr);

        String subCmd = args.get(0);
        CLICommand cmd = CLICommand.clone(subCmd);
        if(cmd!=null) {
            cmd.channel = Channel.current();
            final CLICommand old = CLICommand.setCurrent(cmd);
            try {
                transportAuth = Channel.current().getProperty(CLICommand.TRANSPORT_AUTHENTICATION);
                cmd.setTransportAuth(transportAuth);
                return cmd.main(args.subList(1,args.size()),locale, stdin, out, err);
            } finally {
                CLICommand.setCurrent(old);
            }
        }

        err.println("No such command: "+subCmd);
        new HelpCommand().main(Collections.<String>emptyList(), locale, stdin, out, err);
        return -1;
    }

    public void authenticate(final String protocol, final Pipe c2s, final Pipe s2c) {
        for (final CliTransportAuthenticator cta : CliTransportAuthenticator.all()) {
            if (cta.supportsProtocol(protocol)) {
                new Thread() {
                    @Override
                    public void run() {
                        cta.authenticate(protocol,channel,new Connection(c2s.getIn(), s2c.getOut()));
                    }
                }.start();
                return;
            }
        }
        throw new UnsupportedOperationException("Unsupported authentication protocol: "+protocol);
    }

    public boolean hasCommand(String name) {
        return CLICommand.clone(name)!=null;
    }

    public int protocolVersion() {
        return VERSION;
    }

    private Object writeReplace() {
        return Channel.current().export(CliEntryPoint.class,this);
    }

    private static final Logger LOGGER = Logger.getLogger(CliManagerImpl.class.getName());
}
