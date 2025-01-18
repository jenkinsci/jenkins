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

package hudson.cli.declarative;

import static java.util.logging.Level.SEVERE;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.Functions;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.cli.CloneableCLICommand;
import hudson.model.Hudson;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Stack;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionRefreshException;
import jenkins.model.Jenkins;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Discover {@link CLIMethod}s and register them as {@link CLICommand} implementations.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CLIRegisterer extends ExtensionFinder {
    @Override
    public ExtensionComponentSet refresh() throws ExtensionRefreshException {
        // TODO: this is not complex. just bit tedious.
        return ExtensionComponentSet.EMPTY;
    }

    @Override
    public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson jenkins) {
        if (type == CLICommand.class)
            return (List) discover(jenkins);
        else
            return Collections.emptyList();
    }

    /**
     * Finds a resolved method annotated with {@link CLIResolver}.
     */
    private Method findResolver(Class type) throws IOException {
        List<Method> resolvers = Util.filter(Index.list(CLIResolver.class, Jenkins.get().getPluginManager().uberClassLoader), Method.class);
        for ( ; type != null; type = type.getSuperclass())
            for (Method m : resolvers)
                if (m.getReturnType() == type)
                    return m;
        return null;
    }

    private List<ExtensionComponent<CLICommand>> discover(@NonNull final Jenkins jenkins) {
        LOGGER.fine("Listing up @CLIMethod");
        List<ExtensionComponent<CLICommand>> r = new ArrayList<>();

        try {
            for (final Method m : Util.filter(Index.list(CLIMethod.class, jenkins.getPluginManager().uberClassLoader), Method.class)) {
                try {
                    // command name
                    final String name = m.getAnnotation(CLIMethod.class).name();

                    final ResourceBundleHolder res = loadMessageBundle(m);
                    res.format("CLI." + name + ".shortDescription");   // make sure we have the resource, to fail early

                    r.add(new ExtensionComponent<>(new CloneableCLICommand() {
                        @Override
                        public String getName() {
                            return name;
                        }

                        @Override
                        public String getShortDescription() {
                            // format by using the right locale
                            return res.format("CLI." + name + ".shortDescription");
                        }

                        @Override
                        protected CmdLineParser getCmdLineParser() {
                            return bindMethod(new ArrayList<>());
                        }

                        private CmdLineParser bindMethod(List<MethodBinder> binders) {

                            ParserProperties properties = ParserProperties.defaults().withAtSyntax(ALLOW_AT_SYNTAX);
                            CmdLineParser parser = new CmdLineParser(null, properties);

                            //  build up the call sequence
                            Stack<Method> chains = new Stack<>();
                            Method method = m;
                            while (true) {
                                chains.push(method);
                                if (Modifier.isStatic(method.getModifiers()))
                                    break; // the chain is complete.

                                // the method in question is an instance method, so we need to resolve the instance by using another resolver
                                Class<?> type = method.getDeclaringClass();
                                try {
                                    method = findResolver(type);
                                } catch (IOException ex) {
                                    throw new RuntimeException("Unable to find the resolver method annotated with @CLIResolver for " + type, ex);
                                }
                                if (method == null) {
                                    throw new RuntimeException("Unable to find the resolver method annotated with @CLIResolver for " + type);
                                }
                            }

                            while (!chains.isEmpty())
                                binders.add(new MethodBinder(chains.pop(), this, parser));

                            return parser;
                        }

                        /**
                         * Envelope an annotated CLI command
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
                         *
                         *      <p>
                         *      Jenkins standard exit codes from CLI:
                         *      0 means everything went well.
                         *      1 means further unspecified exception is thrown while performing the command.
                         *      2 means CmdLineException is thrown while performing the command.
                         *      3 means IllegalArgumentException is thrown while performing the command.
                         *      4 mean IllegalStateException is thrown while performing the command.
                         *      5 means AbortException is thrown while performing the command.
                         *      6 means AccessDeniedException is thrown while performing the command.
                         *      7 means BadCredentialsException is thrown while performing the command.
                         *      8-15 are reserved for future usage
                         *      16+ mean a custom CLI exit error code (meaning defined by the CLI command itself)
                         *
                         *      <p>
                         *      Note: For details - see JENKINS-32273
                         */
                        @Override
                        public int main(List<String> args, Locale locale, InputStream stdin, PrintStream stdout, PrintStream stderr) {
                            this.stdout = stdout;
                            this.stderr = stderr;
                            this.locale = locale;

                            List<MethodBinder> binders = new ArrayList<>();

                            CmdLineParser parser = bindMethod(binders);
                            try {
                                // TODO this could probably use ACL.as; why is it calling SecurityContext.setAuthentication rather than SecurityContextHolder.setContext?
                                SecurityContext sc = SecurityContextHolder.getContext();
                                Authentication old = sc.getAuthentication();
                                try {
                                    // fill up all the binders
                                    parser.parseArgument(args);

                                    Authentication auth = getTransportAuthentication2();
                                    sc.setAuthentication(auth); // run the CLI with the right credential
                                    jenkins.checkPermission(Jenkins.READ);

                                    // resolve them
                                    Object instance = null;
                                    for (MethodBinder binder : binders)
                                        instance = binder.call(instance);

                                    if (instance instanceof Integer)
                                        return (Integer) instance;
                                    else
                                        return 0;
                                } catch (InvocationTargetException e) {
                                    Throwable t = e.getTargetException();
                                    if (t instanceof Exception)
                                        throw (Exception) t;
                                    throw e;
                                } finally {
                                    sc.setAuthentication(old); // restore
                                }
                            } catch (CmdLineException e) {
                                printError(e.getMessage());
                                printUsage(stderr, parser);
                                return 2;
                            } catch (IllegalStateException e) {
                                printError(e.getMessage());
                                return 4;
                            } catch (IllegalArgumentException e) {
                                printError(e.getMessage());
                                return 3;
                            } catch (AbortException e) {
                                printError(e.getMessage());
                                return 5;
                            } catch (AccessDeniedException e) {
                                printError(e.getMessage());
                                return 6;
                            } catch (BadCredentialsException e) {
                                // to the caller, we can't reveal whether the user didn't exist or the password didn't match.
                                // do that to the server log instead
                                String id = UUID.randomUUID().toString();
                                logAndPrintError(e, "Bad Credentials. Search the server log for " + id + " for more details.",
                                        "CLI login attempt failed: " + id, Level.INFO);
                                return 7;
                            } catch (Throwable e) {
                                final String errorMsg = "Unexpected exception occurred while performing " + getName() + " command.";
                                logAndPrintError(e, errorMsg, errorMsg, Level.WARNING);
                                Functions.printStackTrace(e, stderr);
                                return 1;
                            }
                        }

                        private void printError(String errorMessage) {
                            this.stderr.println();
                            this.stderr.println("ERROR: " + errorMessage);
                        }

                        private void logAndPrintError(Throwable e, String errorMessage, String logMessage, Level logLevel) {
                            LOGGER.log(logLevel, logMessage, e);
                            printError(errorMessage);
                        }

                        @Override
                        protected int run() throws Exception {
                            throw new UnsupportedOperationException();
                        }
                    }));
                } catch (ClassNotFoundException | MissingResourceException e) {
                    LOGGER.log(SEVERE, "Failed to process @CLIMethod: " + m, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to discover @CLIMethod", e);
        }

        return r;
    }

    /**
     * Locates the {@link ResourceBundleHolder} for this CLI method.
     */
    private ResourceBundleHolder loadMessageBundle(Method m) throws ClassNotFoundException {
        Class c = m.getDeclaringClass();
        Class<?> msg = c.getClassLoader().loadClass(c.getName().substring(0, c.getName().lastIndexOf(".")) + ".Messages");
        return ResourceBundleHolder.get(msg);
    }

    private static final Logger LOGGER = Logger.getLogger(CLIRegisterer.class.getName());
}
