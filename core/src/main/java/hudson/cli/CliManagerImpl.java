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

import hudson.model.User;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import org.apache.commons.discovery.resource.ClassLoaders;
import org.apache.commons.discovery.resource.classes.DiscoverClasses;
import org.apache.commons.discovery.resource.names.DiscoverServiceNames;
import org.apache.commons.discovery.ResourceNameIterator;
import org.apache.commons.discovery.ResourceClassIterator;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.CmdLineParser;
import org.jvnet.tiger_types.Types;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * {@link CliEntryPoint} implementation exposed to the remote CLI.
 *
 * @author Kohsuke Kawaguchi
 */
public class CliManagerImpl implements CliEntryPoint, Serializable {
    private final Channel channel;

    public CliManagerImpl(Channel channel) {
        this.channel = channel;
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
            final CLICommand old = CLICommand.setCurrent(cmd);
            try {
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

    static {
        // register option handlers that are defined
        ClassLoaders cls = new ClassLoaders();
        cls.put(Jenkins.getInstance().getPluginManager().uberClassLoader);

        ResourceNameIterator servicesIter =
            new DiscoverServiceNames(cls).findResourceNames(OptionHandler.class.getName());
        final ResourceClassIterator itr =
            new DiscoverClasses(cls).findResourceClasses(servicesIter);

        while(itr.hasNext()) {
            Class h = itr.nextResourceClass().loadClass();
            Class c = Types.erasure(Types.getTypeArgument(Types.getBaseClass(h, OptionHandler.class), 0));
            CmdLineParser.registerHandler(c,h);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CliManagerImpl.class.getName());
}
