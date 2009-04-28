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

import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.remoting.Channel;
import hudson.remoting.Callable;
import hudson.model.Hudson;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.PrintStream;
import java.util.List;

/**
 * Base class for Hudson CLI.
 *
 * <h2>How to implement a CLI command</h2>
 * <p>
 * CLI commands are defined on the server.
 *
 * <p>
 * Use <a href="http://args4j.dev.java.net/">args4j</a> annotation on your implementation to define
 * options and arguments (however, if you don't like that, you could override the {@link #main(List, PrintStream, PrintStream)} method
 * directly.
 *
 * <p>
 * Put {@link Extension} on your implementation to have it auto-registered.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.302
 */
public abstract class CLICommand implements ExtensionPoint, Cloneable {
    /**
     * Connected to stdout and stderr of the CLI agent that initiated the session.
     * IOW, if you write to these streams, the person who launched the CLI command
     * will see the messages in his terminal.
     *
     * (In contrast, calling {@code System.out.println(...)} would print out
     * the message to the server log file, which is probably not what you want.
     */
    protected PrintStream stdout,stderr;

    /**
     * {@link Channel} that represents the CLI JVM. You can use this to
     * execute {@link Callable} on the CLI JVM, among other things.
     */
    protected Channel channel;


    /**
     * Gets the command name.
     *
     * <p>
     * For example, if the CLI is invoked as <tt>java -jar cli.jar foo arg1 arg2 arg4</tt>,
     * on the server side {@link CLICommand} that returns "foo" from {@link #getName()}
     * will be invoked.
     *
     * <p>
     * By default, this method creates "foo-bar-zot" from "FooBarZotCommand".
     */
    public String getName() {
        String name = getClass().getName();
        name = name.substring(name.lastIndexOf('.')+1); // short name
        if(name.endsWith("Command"))
            name = name.substring(0,name.length()-7); // trim off the command

        // convert "FooBarZot" into "foo-bar-zot"
        return name.replaceAll("([a-z0-9])([A-Z])","$1-$2").toLowerCase();
    }

    public int main(List<String> args, PrintStream stdout, PrintStream stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.channel = Channel.current();
        CmdLineParser p = new CmdLineParser(this);
        try {
            p.parseArgument(args.toArray(new String[args.size()]));
            return run();
        } catch (CmdLineException e) {
            stderr.println(e.getMessage());
            printUsage(stderr, p);
            return -1;
        }
    }

    /**
     * Executes the command, and return the exit code.
     *
     * @return
     *      0 to indicate a success, otherwise an error code.
     */
    protected abstract int run();

    protected void printUsage(PrintStream stderr, CmdLineParser p) {
        stderr.println("java -jar hudson-cli.jar "+getName()+" args...");
        p.printUsage(stderr);
    }

    /**
     * Returns all the registered {@link CLICommand}s.
     */
    public static ExtensionList<CLICommand> all() {
        return Hudson.getInstance().getExtensionList(CLICommand.class);
    }

    /**
     * Obtains a copy of the command for invocation.
     */
    public static CLICommand clone(String name) {
        for (CLICommand cmd : all()) {
            if(name.equals(cmd.getName())) {
                try {
                    return (CLICommand)cmd.clone();
                } catch (CloneNotSupportedException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return null;
    }
}
