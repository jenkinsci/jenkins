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
import hudson.AbortException;
import hudson.remoting.Channel;
import hudson.remoting.Callable;
import hudson.model.Hudson;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.PrintStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.List;
import java.util.Locale;

/**
 * Base class for Hudson CLI.
 *
 * <h2>How does a CLI command work</h2>
 * <p>
 * The users starts {@linkplain CLI the "CLI agent"} on a remote system, by specifying arguments, like
 * <tt>"java -jar hudson-cli.jar command arg1 arg2 arg3"</tt>. The CLI agent creates
 * a remoting channel with the server, and it sends the entire arguments to the server, along with
 * the remoted stdin/out/err.
 *
 * <p>
 * The Hudson master then picks the right {@link CLICommand} to execute, clone it, and
 * calls {@link #main(List, InputStream, PrintStream, PrintStream)} method.
 *
 * <h2>Note for CLI command implementor</h2>
 * <ul>
 * <li>
 * Put {@link Extension} on your implementation to have it discovered by Hudson.
 *
 * <li>
 * Use <a href="http://args4j.dev.java.net/">args4j</a> annotation on your implementation to define
 * options and arguments (however, if you don't like that, you could override
 * the {@link #main(List, InputStream, PrintStream, PrintStream)} method directly.
 *
 * <li>
 * stdin, stdout, stderr are remoted, so proper buffering is necessary for good user experience.
 *
 * <li>
 * Send {@link Callable} to a CLI agent by using {@link #channel} to get local interaction,
 * such as uploading a file, asking for a password, etc.
 *
 * </ul>
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
     * <p>
     * (In contrast, calling {@code System.out.println(...)} would print out
     * the message to the server log file, which is probably not what you want.
     */
    protected transient PrintStream stdout,stderr;

    /**
     * Connected to stdin of the CLI agent.
     *
     * <p>
     * This input stream is buffered to hide the latency in the remoting.
     */
    protected transient InputStream stdin;

    /**
     * {@link Channel} that represents the CLI JVM. You can use this to
     * execute {@link Callable} on the CLI JVM, among other things.
     */
    protected transient Channel channel;


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
        // Locale is fixed so that "CreateInstance" always become "create-instance" no matter where this is run.
        return name.replaceAll("([a-z0-9])([A-Z])","$1-$2").toLowerCase(Locale.ENGLISH);
    }

    /**
     * Gets the quick summary of what this command does.
     * Used by the help command to generate the list of commands.
     */
    public abstract String getShortDescription();

    public int main(List<String> args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        this.stdin = new BufferedInputStream(stdin);
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
        } catch (AbortException e) {
            // signals an error without stack trace
            stderr.println(e.getMessage());
            return -1;
        } catch (Exception e) {
            e.printStackTrace(stderr);
            return -1;
        }
    }

    /**
     * Executes the command, and return the exit code.
     *
     * @return
     *      0 to indicate a success, otherwise an error code.
     * @throws AbortException
     *      If the processing should be aborted. Hudson will report the error message
     *      without stack trace, and then exits this command.
     * @throws Exception
     *      All the other exceptions cause the stack trace to be dumped, and then
     *      the command exits with an error code.
     */
    protected abstract int run() throws Exception;

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
                    return cmd.getClass().newInstance();
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (InstantiationException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return null;
    }
}
