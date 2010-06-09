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

import hudson.model.Hudson;
import hudson.remoting.Channel;
import org.acegisecurity.Authentication;
import org.apache.commons.discovery.ResourceClassIterator;
import org.apache.commons.discovery.ResourceNameIterator;
import org.apache.commons.discovery.resource.ClassLoaders;
import org.apache.commons.discovery.resource.classes.DiscoverClasses;
import org.apache.commons.discovery.resource.names.DiscoverServiceNames;
import org.jvnet.tiger_types.Types;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.spi.OptionHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@link CliEntryPoint} implementation exposed to the remote CLI.
 *
 * @author Kohsuke Kawaguchi
 */
public class CliManagerImpl implements CliEntryPoint, Serializable {

    private final Authentication auth;

    public CliManagerImpl(Authentication auth) {
        this.auth = auth!=null ? auth : Hudson.ANONYMOUS;
    }

    public CliManagerImpl() {
        this(null);
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
            // execute the command, do so with the originator of the request as the principal
            return cmd.main(args.subList(1,args.size()),locale, stdin, out, err, auth);
        }

        err.println("No such command: "+subCmd);
        new HelpCommand().main(Collections.<String>emptyList(), locale, stdin, out, err, auth);
        return -1;
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
        cls.put(Hudson.getInstance().getPluginManager().uberClassLoader);

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
}


