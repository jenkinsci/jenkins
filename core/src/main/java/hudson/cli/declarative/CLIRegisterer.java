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

import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.Functions;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.cli.CloneableCLICommand;
import hudson.model.Hudson;
import hudson.security.CliAuthenticator;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionRefreshException;
import jenkins.cli.CLIReturnCode;
import jenkins.cli.CLIReturnCodeStandard;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.args4j.ClassParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

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

import static java.util.logging.Level.SEVERE;

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

    public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson jenkins) {
        if (type==CLICommand.class)
            return (List)discover(jenkins);
        else
            return Collections.emptyList();
    }

    /**
     * Finds a resolved method annotated with {@link CLIResolver}.
     */
    private Method findResolver(Class type) throws IOException {
        List<Method> resolvers = Util.filter(Index.list(CLIResolver.class, Jenkins.getInstance().getPluginManager().uberClassLoader), Method.class);
        for ( ; type!=null; type=type.getSuperclass())
            for (Method m : resolvers)
                if (m.getReturnType()==type)
                    return m;
        return null;
    }

    private List<ExtensionComponent<CLICommand>> discover(final Jenkins hudson) {
        LOGGER.fine("Listing up @CLIMethod");
        List<ExtensionComponent<CLICommand>> r = new ArrayList<ExtensionComponent<CLICommand>>();

        try {
            for ( final Method m : Util.filter(Index.list(CLIMethod.class, hudson.getPluginManager().uberClassLoader),Method.class)) {
                try {
                    // command name
                    final String name = m.getAnnotation(CLIMethod.class).name();

                    final ResourceBundleHolder res = loadMessageBundle(m);
                    res.format("CLI."+name+".shortDescription");   // make sure we have the resource, to fail early

                    r.add(new ExtensionComponent<CLICommand>(new CloneableCLICommand() {
                        @Override
                        public String getName() {
                            return name;
                        }

                        @Override
                        public String getShortDescription() {
                            // format by using the right locale
                            return res.format("CLI."+name+".shortDescription");
                        }

                        @Override
                        protected CmdLineParser getCmdLineParser() {
                            return bindMethod(new ArrayList<MethodBinder>());
                        }

                        private CmdLineParser bindMethod(List<MethodBinder> binders) {

                            registerOptionHandlers();
                            CmdLineParser parser = new CmdLineParser(null);

                            //  build up the call sequence
                            Stack<Method> chains = new Stack<Method>();
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
                                    throw new RuntimeException("Unable to find the resolver method annotated with @CLIResolver for "+type, ex);
                                }
                                if (method==null) {
                                    throw new RuntimeException("Unable to find the resolver method annotated with @CLIResolver for "+type);
                                }
                            }

                            while (!chains.isEmpty())
                                binders.add(new MethodBinder(chains.pop(),this,parser));

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
                         * @see CLIReturnCode
                         */
                        @Override
                        public int main(List<String> args, Locale locale, InputStream stdin, PrintStream stdout, PrintStream stderr) {
                            this.stdout = stdout;
                            this.stderr = stderr;
                            this.locale = locale;

                            List<MethodBinder> binders = new ArrayList<MethodBinder>();

                            CmdLineParser parser = bindMethod(binders);
                            SecurityContext sc = null;
                            Authentication old = null;
                            try {
                                sc = SecurityContextHolder.getContext();
                                old = sc.getAuthentication();
                                try {
                                    // authentication
                                    CliAuthenticator authenticator = Jenkins.get().getSecurityRealm().createCliAuthenticator(this);
                                    new ClassParser().parse(authenticator, parser);

                                    // fill up all the binders
                                    parser.parseArgument(args);

                                    Authentication auth = authenticator.authenticate();
                                    if (auth == Jenkins.ANONYMOUS)
                                        auth = loadStoredAuthentication();
                                    sc.setAuthentication(auth); // run the CLI with the right credential
                                    hudson.checkPermission(Jenkins.READ);

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
                                }
                            } catch (CmdLineException e) {
                                stderr.println();
                                stderr.println("ERROR: " + e.getMessage());
                                printUsage(stderr, parser);
                                return CLIReturnCodeStandard.WRONG_CMD_PARAMETER.getCode();
                            } catch (IllegalStateException e) {
                                stderr.println();
                                stderr.println("ERROR: " + e.getMessage());
                                return CLIReturnCodeStandard.ILLEGAL_STATE.getCode();
                            } catch (IllegalArgumentException e) {
                                stderr.println();
                                stderr.println("ERROR: " + e.getMessage());
                                return CLIReturnCodeStandard.ILLEGAL_ARGUMENT.getCode();
                            } catch (AbortException e) {
                                stderr.println();
                                stderr.println("ERROR: " + e.getMessage());
                                return CLIReturnCodeStandard.ABORTED.getCode();
                            } catch (AccessDeniedException e) {
                                stderr.println();
                                stderr.println("ERROR: " + e.getMessage());
                                return CLIReturnCodeStandard.ACCESS_DENIED.getCode();
                            } catch (BadCredentialsException e) {
                                // to the caller, we can't reveal whether the user didn't exist or the password didn't match.
                                // do that to the server log instead
                                String id = UUID.randomUUID().toString();
                                LOGGER.log(Level.INFO, "CLI login attempt failed: " + id, e);
                                stderr.println();
                                stderr.println("ERROR: Bad Credentials. Search the server log for " + id + " for more details.");
                                return CLIReturnCodeStandard.BAD_CREDENTIALS.getCode();
                            } catch (Throwable e) {
                                final String errorMsg = String.format("Unexpected exception occurred while performing %s command.",
                                        getName());
                                stderr.println();
                                stderr.println("ERROR: " + errorMsg);
                                LOGGER.log(Level.WARNING, errorMsg, e);
                                Functions.printStackTrace(e, stderr);
                                return CLIReturnCodeStandard.UNKNOWN_ERROR_OCCURRED.getCode();
                            } finally {
                                if (sc != null) {
                                    // restore previous one
                                    sc.setAuthentication(old);
                                }
                            }
                        }

                        protected int run() throws Exception {
                            throw new UnsupportedOperationException();
                        }

                        protected CLIReturnCode execute() throws Exception {
                            throw new UnsupportedOperationException();
                        }
                    }));
                } catch (ClassNotFoundException | MissingResourceException e) {
                    LOGGER.log(SEVERE,"Failed to process @CLIMethod: "+m,e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to discover @CLIMethod",e);
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
