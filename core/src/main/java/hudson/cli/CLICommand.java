/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.ExtensionPoint.LegacyInstancesAreScopedToHudson;
import hudson.Functions;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.OptionHandlerExtension;
import hudson.remoting.Channel;
import hudson.security.SecurityRealm;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import jenkins.cli.listeners.CLIContext;
import jenkins.cli.listeners.CLIListener;
import jenkins.model.Jenkins;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.OptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Base class for Hudson CLI.
 *
 * <h2>How does a CLI command work</h2>
 * <p>
 * The users starts {@linkplain CLI the "CLI agent"} on a remote system, by specifying arguments, like
 * {@code "java -jar jenkins-cli.jar command arg1 arg2 arg3"}. The CLI agent creates
 * a connection to the server, and it sends the entire arguments to the server, along with
 * the remoted stdin/out/err.
 *
 * <p>
 * The Hudson master then picks the right {@link CLICommand} to execute, clone it, and
 * calls {@link #main(List, Locale, InputStream, PrintStream, PrintStream)} method.
 *
 * <h2>Note for CLI command implementor</h2>
 * Start with <a href="https://www.jenkins.io/doc/developer/cli/writing-cli-commands/">this document</a>
 * to get the general idea of CLI.
 *
 * <ul>
 * <li>
 * Put {@link Extension} on your implementation to have it discovered by Hudson.
 *
 * <li>
 * Use <a href="https://github.com/kohsuke/args4j">args4j</a> annotation on your implementation to define
 * options and arguments (however, if you don't like that, you could override
 * the {@link #main(List, Locale, InputStream, PrintStream, PrintStream)} method directly.
 *
 * <li>
 * stdin, stdout, stderr are remoted, so proper buffering is necessary for good user experience.
 *
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.302
 * @see CLIMethod
 */
@LegacyInstancesAreScopedToHudson
public abstract class CLICommand implements ExtensionPoint, Cloneable {

    /**
     * Boolean values to either allow or disallow parsing of @-prefixes.
     * If a command line value starts with @, it is interpreted as being a file, loaded,
     * and interpreted as if the file content would have been passed to the command line
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    @Restricted(NoExternalUse.class)
    public static boolean ALLOW_AT_SYNTAX = SystemProperties.getBoolean(CLICommand.class.getName() + ".allowAtSyntax");

    /**
     * Connected to stdout and stderr of the CLI agent that initiated the session.
     * IOW, if you write to these streams, the person who launched the CLI command
     * will see the messages in his terminal.
     *
     * <p>
     * (In contrast, calling {@code System.out.println(...)} would print out
     * the message to the server log file, which is probably not what you want.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public transient PrintStream stdout, stderr;

    /**
     * Shared text, which is reported back to CLI if an error happens in commands
     * taking lists of parameters.
     * @since 2.26
     */
    static final String CLI_LISTPARAM_SUMMARY_ERROR_TEXT = "Error occurred while performing this command, see previous stderr output.";

    /**
     * Connected to stdin of the CLI agent.
     *
     * <p>
     * This input stream is buffered to hide the latency in the remoting.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public transient InputStream stdin;

    /**
     * @deprecated No longer used.
     */
    @Deprecated
    public transient Channel channel;

    /**
     * The locale of the client. Messages should be formatted with this resource.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public transient Locale locale;

    /**
     * The encoding of the client, if defined.
     */
    private transient @CheckForNull Charset encoding;

    /**
     * Set by the caller of the CLI system if the transport already provides
     * authentication.
     */
    private transient Authentication transportAuth;

    /**
     * Gets the command name.
     *
     * <p>
     * For example, if the CLI is invoked as {@code java -jar cli.jar foo arg1 arg2 arg4},
     * on the server side {@link CLICommand} that returns "foo" from {@link #getName()}
     * will be invoked.
     *
     * <p>
     * By default, this method creates "foo-bar-zot" from "FooBarZotCommand".
     */
    public String getName() {
        String name = getClass().getName();
        name = name.substring(name.lastIndexOf('.') + 1); // short name
        name = name.substring(name.lastIndexOf('$') + 1);
        if (name.endsWith("Command"))
            name = name.substring(0, name.length() - 7); // trim off the command

        // convert "FooBarZot" into "foo-bar-zot"
        // Locale is fixed so that "CreateInstance" always become "create-instance" no matter where this is run.
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ENGLISH);
    }

    /**
     * Gets the quick summary of what this command does.
     * Used by the help command to generate the list of commands.
     */
    public abstract String getShortDescription();

    /**
     * Entry point to the CLI command.
     *
     * <p>
     * The default implementation uses args4j to parse command line arguments and call {@link #run()},
     * but if that processing is undesirable, subtypes can directly override this method and leave {@link #run()}
     * to an empty method.
     * You would however then have to consider {@link #getTransportAuthentication2},
     * so this is not really recommended.
     *
     * @param args
     *      Arguments to the sub command. For example, if the CLI is invoked like "java -jar cli.jar foo bar zot",
     *      then "foo" is the sub-command and the argument list is ["bar","zot"].
     * @param locale
     *      Locale of the client (which can be different from that of the server.) Good behaving command implementation
     *      would use this locale for formatting messages.
     * @param stdin
     *      Connected to the stdin of the CLI client.
     * @param stdout
     *      Connected to the stdout of the CLI client.
     * @param stderr
     *      Connected to the stderr of the CLI client.
     * @return
     *      Exit code from the CLI command execution
     *      <table>
     *      <caption>Jenkins standard exit codes from CLI</caption>
     *      <tr><th>Code</th><th>Definition</th></tr>
     *      <tr><td>0</td><td>everything went well.</td></tr>
     *      <tr><td>1</td><td>further unspecified exception is thrown while performing the command.</td></tr>
     *      <tr><td>2</td><td>{@link CmdLineException} is thrown while performing the command.</td></tr>
     *      <tr><td>3</td><td>{@link IllegalArgumentException} is thrown while performing the command.</td></tr>
     *      <tr><td>4</td><td>{@link IllegalStateException} is thrown while performing the command.</td></tr>
     *      <tr><td>5</td><td>{@link AbortException} is thrown while performing the command.</td></tr>
     *      <tr><td>6</td><td>{@link AccessDeniedException} is thrown while performing the command.</td></tr>
     *      <tr><td>7</td><td>{@link BadCredentialsException} is thrown while performing the command.</td></tr>
     *      <tr><td>8-15</td><td>are reserved for future usage.</td></tr>
     *      <tr><td>16+</td><td>a custom CLI exit error code (meaning defined by the CLI command itself)</td></tr>
     *      </table>
     *      Note: For details - see JENKINS-32273
     */
    public int main(List<String> args, Locale locale, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        this.stdin = new BufferedInputStream(stdin);
        this.stdout = stdout;
        this.stderr = stderr;
        this.locale = locale;
        CmdLineParser p = getCmdLineParser();

        CLIContext context = new CLIContext(getName(), args, getTransportAuthentication2());

        // add options from the authenticator
        SecurityContext sc = null;
        Authentication old = null;
        try {
            // TODO as in CLIRegisterer this may be doing too much work
            sc = SecurityContextHolder.getContext();
            old = sc.getAuthentication();

            sc.setAuthentication(getTransportAuthentication2());

            if (!(this instanceof HelpCommand || this instanceof WhoAmICommand))
                Jenkins.get().checkPermission(Jenkins.READ);
            p.parseArgument(args.toArray(new String[0]));

            Listeners.notify(CLIListener.class, true, listener -> listener.onExecution(context));
            int res = run();
            Listeners.notify(CLIListener.class, true, listener -> listener.onCompleted(context, res));

            return res;
        } catch (Throwable e) {
            int exitCode = handleException(e, context, p);
            Listeners.notify(CLIListener.class, true, listener -> listener.onException(context, e));
            return exitCode;
        } finally {
            if (sc != null)
                sc.setAuthentication(old); // restore
        }
    }

    /**
     * Determines command stderr output and return the exit code as described on {@link #main(List, Locale, InputStream, PrintStream, PrintStream)}
     * */
    protected int handleException(Throwable e, CLIContext context, CmdLineParser p) {
        int exitCode;
        if (e instanceof CmdLineException) {
            exitCode = 2;
            printError(e.getMessage());
            printUsage(stderr, p);
        } else if (e instanceof IllegalArgumentException) {
            exitCode = 3;
            printError(e.getMessage());
        } else if (e instanceof IllegalStateException) {
            exitCode = 4;
            printError(e.getMessage());
        } else if (e instanceof AbortException) {
            exitCode = 5;
            printError(e.getMessage());
        } else if (e instanceof AccessDeniedException) {
            exitCode = 6;
            printError(e.getMessage());
        } else if (e instanceof BadCredentialsException) {
            exitCode = 7;
            printError(
                    "Bad Credentials. Search the server log for " + context.getCorrelationId() + " for more details.");
        } else {
            exitCode = 1;
            printError("Unexpected exception occurred while performing " + getName() + " command.");
            Functions.printStackTrace(e, stderr);
        }
        return exitCode;
    }


    private void printError(String errorMessage) {
        this.stderr.println();
        this.stderr.println("ERROR: " + errorMessage);
    }

    /**
     * Get parser for this command.
     *
     * Exposed to be overridden by {@link hudson.cli.declarative.CLIRegisterer}.
     * @since 1.538
     */
    protected CmdLineParser getCmdLineParser() {
        ParserProperties properties = ParserProperties.defaults().withAtSyntax(ALLOW_AT_SYNTAX);
        return new CmdLineParser(this, properties);
    }

    /**
     * @deprecated Specific to Remoting-based protocol.
     */
    @Deprecated
    public Channel checkChannel() throws AbortException {
        throw new AbortException("This command is requesting the -remoting mode which is no longer supported. See https://www.jenkins.io/redirect/cli-command-requires-channel");
    }

    /**
     * Returns the identity of the client as determined at the CLI transport level.
     *
     * <p>
     * When the CLI connection to the server is tunneled over HTTP, that HTTP connection
     * can authenticate the client, just like any other HTTP connections to the server
     * can authenticate the client. This method returns that information, if one is available.
     * By generalizing it, this method returns the identity obtained at the transport-level authentication.
     *
     * <p>
     * For example, imagine if the current {@link SecurityRealm} is doing Kerberos authentication,
     * then this method can return a valid identity of the client.
     *
     * <p>
     * If the transport doesn't do authentication, this method returns {@link jenkins.model.Jenkins#ANONYMOUS2}.
     * @since 2.266
     */
    public Authentication getTransportAuthentication2() {
        Authentication a = transportAuth;
        if (a == null)    a = Jenkins.ANONYMOUS2;
        return a;
    }

    /**
     * @deprecated use {@link #getTransportAuthentication2}
     */
    @Deprecated
    public org.acegisecurity.Authentication getTransportAuthentication() {
        return org.acegisecurity.Authentication.fromSpring(getTransportAuthentication2());
    }

    /**
     * @since 2.266
     */
    public void setTransportAuth2(Authentication transportAuth) {
        this.transportAuth = transportAuth;
    }

    /**
     * @deprecated use {@link #setTransportAuth2}
     */
    @Deprecated
    public void setTransportAuth(org.acegisecurity.Authentication transportAuth) {
        setTransportAuth2(transportAuth.toSpring());
    }

    /**
     * Executes the command, and return the exit code.
     *
     * <p>
     * This is an internal contract between {@link CLICommand} and its subtype.
     * To execute CLI method from outside, use {@link #main(List, Locale, InputStream, PrintStream, PrintStream)}
     *
     * @return
     *      0 to indicate a success, otherwise a custom error code.
     *      Error codes 1-15 shouldn;t be used in {@link #run()} as a custom error code.
     * @throws Exception
     *      If a further unspecified exception is thrown; means: Unknown and/or unexpected issue occurred
     * @throws CmdLineException
     *      If a wrong parameter specified, input value can't be decoded etc.
     * @throws IllegalArgumentException
     *      If the execution can't continue due to wrong input parameter (job doesn't exist etc.)
     * @throws IllegalStateException
     *      If the execution can't continue due to an incorrect state of Jenkins, job, build etc.
     * @throws AbortException
     *      If the execution can't continue due to an other (rare, but foreseeable) issue
     * @throws AccessDeniedException
     *      If the caller doesn't have sufficient rights for requested action
     * @throws BadCredentialsException
     *      If bad credentials were provided to CLI
     */
    protected abstract int run() throws Exception;

    protected void printUsage(PrintStream stderr, CmdLineParser p) {
        stderr.print("java -jar jenkins-cli.jar " + getName());
        p.printSingleLineUsage(stderr);
        stderr.println();
        printUsageSummary(stderr);
        p.printUsage(stderr);
    }

    /**
     * Get single line summary as a string.
     */
    @Restricted(NoExternalUse.class)
    public final String getSingleLineSummary() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getCmdLineParser().printSingleLineUsage(out);
        Charset charset;
        try {
            charset = getClientCharset();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return out.toString(charset);
    }

    /**
     * Get usage as a string.
     */
    @Restricted(NoExternalUse.class)
    public final String getUsage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        getCmdLineParser().printUsage(out);
        Charset charset;
        try {
            charset = getClientCharset();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return out.toString(charset);
    }

    /**
     * Get long description as a string.
     */
    @Restricted(NoExternalUse.class)
    public final String getLongDescription() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Charset charset;
        try {
            charset = getClientCharset();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        PrintStream ps = new PrintStream(out, false, charset);

        printUsageSummary(ps);
        ps.close();
        return out.toString(charset);
    }

    /**
     * Called while producing usage. This is a good method to override
     * to render the general description of the command that goes beyond
     * a single-line summary.
     */
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(getShortDescription());
    }

    /**
     * Convenience method for subtypes to obtain the system property of the client.
     * @deprecated Specific to Remoting-based protocol.
     */
    @Deprecated
    protected String getClientSystemProperty(String name) throws IOException, InterruptedException {
        checkChannel();
        return null; // never run
    }

    /**
     * Define the encoding for the command.
     * @since 2.54
     */
    public void setClientCharset(@NonNull Charset encoding) {
        this.encoding = encoding;
    }

    protected @NonNull Charset getClientCharset() throws IOException, InterruptedException {
        if (encoding != null) {
            return encoding;
        }
        // for SSH, assume the platform default encoding
        // this is in-line with the standard SSH behavior
        return Charset.defaultCharset();
    }

    /**
     * Convenience method for subtypes to obtain environment variables of the client.
     * @deprecated Specific to Remoting-based protocol.
     */
    @Deprecated
    protected String getClientEnvironmentVariable(String name) throws IOException, InterruptedException {
        checkChannel();
        return null; // never run
    }

    /**
     * Creates a clone to be used to execute a command.
     */
    protected CLICommand createClone() {
        try {
            return getClass().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    /**
     * Returns all the registered {@link CLICommand}s.
     */
    public static ExtensionList<CLICommand> all() {
        return ExtensionList.lookup(CLICommand.class);
    }

    /**
     * Obtains a copy of the command for invocation.
     */
    public static CLICommand clone(String name) {
        for (CLICommand cmd : all())
            if (name.equals(cmd.getName()))
                return cmd.createClone();
        return null;
    }

    private static final ThreadLocal<CLICommand> CURRENT_COMMAND = new ThreadLocal<>();

    /*package*/ static CLICommand setCurrent(CLICommand cmd) {
        CLICommand old = getCurrent();
        CURRENT_COMMAND.set(cmd);
        return old;
    }

    /**
     * If the calling thread is in the middle of executing a CLI command, return it. Otherwise null.
     */
    public static CLICommand getCurrent() {
        return CURRENT_COMMAND.get();
    }

    static {
        // register option handlers that are defined
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j != null) { // only when running on the controller
            // Register OptionHandlers through META-INF/services/annotations and Annotation Indexer
            try {
                for (Class c : Index.list(OptionHandlerExtension.class, j.getPluginManager().uberClassLoader, Class.class)) {
                    Type t = Types.getBaseClass(c, OptionHandler.class);
                    CmdLineParser.registerHandler(Types.erasure(Types.getTypeArgument(t, 0)), c);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
