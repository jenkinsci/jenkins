/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Oracle Corporation
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
package hudson.security;

import hudson.cli.CLICommand;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;

/**
 * Handles authentication for CLI commands.
 *
 * <p>
 * {@link CliAuthenticator} is used to authenticate an invocation of the CLI command, so that
 * the thread carries the correct {@link Authentication} that represents the user who's invoking the command.
 *
 * <h2>Lifecycle</h2>
 * <p>
 * Each time a CLI command is invoked, {@link SecurityRealm#createCliAuthenticator(CLICommand)} is called
 * to allocate a fresh {@link CliAuthenticator} object.
 *
 * <p>
 * The {@link Option} and {@link Argument} annotations on the returned {@link CliAuthenticator} instance are
 * scanned and added into the {@link CmdLineParser}, then that parser is used to parse command line arguments.
 * This means subtypes can define fields/setters with those annotations to define authentication-specific options
 * to CLI commands.
 *
 * <p>
 * Once the arguments and options are parsed and populated, {@link #authenticate()} method is called to
 * perform the authentications. If the authentication succeeds, this method returns an {@link Authentication}
 * instance that represents the user. If the authentication fails, this method throws {@link AuthenticationException}.
 * To authenticate, the method can use parsed argument/option values, as well as interacting with the client
 * via {@link CLICommand} by using its stdin/stdout and its channel (for example, if you want to interactively prompt
 * a password, you can do so by using {@link CLICommand#channel}.)
 *
 * <p>
 * If no explicit credential is provided, or if the {@link SecurityRealm} depends on a mode of authentication
 * that doesn't involve in explicit password (such as Kerberos), it's also often useful to fall back to
 * {@link CLICommand#getTransportAuthentication()}, in case the user is authenticated at the transport level.
 *
 * <p>
 * Many commands do not require any authentication (for example, the "help" command), and still more commands
 * can be run successfully with the anonymous permission. So the authenticator should normally allow unauthenticated
 * CLI command invocations. For those, return {@link jenkins.model.Jenkins#ANONYMOUS} from the {@link #authenticate()} method.
 *
 * <h2>Example</h2>
 * <p>
 * For a complete example, see the implementation of
 * {@link AbstractPasswordBasedSecurityRealm#createCliAuthenticator(CLICommand)}
 *
 * @author Kohsuke Kawaguchi
 * @since 1.350
 */
public abstract class CliAuthenticator {
    /**
     * Authenticates the CLI invocation. See class javadoc for the semantics.
     *
     * @throws AuthenticationException
     *      If the authentication failed and hence the processing shouldn't proceed.
     * @throws IOException
     *      Can be thrown if the {@link CliAuthenticator} fails to interact with the client.
     *      This exception is treated as a failure of authentication. It's just that allowing this
     *      would often simplify the callee.
     * @throws InterruptedException
     *      Same motivation as {@link IOException}. Treated as an authentication failure. 
     */
    public abstract Authentication authenticate() throws AuthenticationException, IOException, InterruptedException;
}
