/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import hudson.model.UnprotectedRootAction;
import jenkins.model.Jenkins;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.Extension;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.FullDuplexHttpService;
import org.kohsuke.stapler.HttpResponses;

/**
 * Shows usage of CLI and commands.
 *
 * @author ogondza
 */
@Extension @Symbol("cli")
@Restricted(NoExternalUse.class)
public class CLIAction implements UnprotectedRootAction, StaplerProxy {

    private static final Logger LOGGER = Logger.getLogger(CLIAction.class.getName());

    private transient final Map<UUID, FullDuplexHttpService> duplexServices = new HashMap<>();

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Jenkins CLI";
    }

    public String getUrlName() {
        return "cli";
    }

    public void doCommand(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        final Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.READ);

        // Strip trailing slash
        final String commandName = req.getRestOfPath().substring(1);
        CLICommand command = CLICommand.clone(commandName);
        if (command == null) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such command");
            return;
        }

        req.setAttribute("command", command);
        req.getView(this, "command.jelly").forward(req, rsp);
    }

    @Override
    public Object getTarget() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req.getRestOfPath().length()==0 && "POST".equals(req.getMethod())) {
            // CLI connection request
            if ("false".equals(req.getParameter("remoting"))) {
                throw new PlainCliEndpointResponse();
            } else {
                // remoting=true (the historical default) no longer supported.
                throw HttpResponses.forbidden();
            }
        } else {
            return this;
        }
    }

    /**
     * Serves {@link PlainCLIProtocol} response.
     */
    private class PlainCliEndpointResponse extends FullDuplexHttpService.Response {

        PlainCliEndpointResponse() {
            super(duplexServices);
        }

        @Override
        protected FullDuplexHttpService createService(StaplerRequest req, UUID uuid) throws IOException {
            return new FullDuplexHttpService(uuid) {
                @Override
                protected void run(InputStream upload, OutputStream download) throws IOException, InterruptedException {
                    final AtomicReference<Thread> runningThread = new AtomicReference<>();
                    class ServerSideImpl extends PlainCLIProtocol.ServerSide {
                        boolean ready;
                        List<String> args = new ArrayList<>();
                        Locale locale = Locale.getDefault();
                        Charset encoding = Charset.defaultCharset();
                        final PipedInputStream stdin = new PipedInputStream();
                        final PipedOutputStream stdinMatch = new PipedOutputStream();
                        ServerSideImpl(InputStream is, OutputStream os) throws IOException {
                            super(is, os);
                            stdinMatch.connect(stdin);
                        }
                        @Override
                        protected void onArg(String text) {
                            args.add(text);
                        }
                        @Override
                        protected void onLocale(String text) {
                            for (Locale _locale : Locale.getAvailableLocales()) {
                                if (_locale.toString().equals(text)) {
                                    locale = _locale;
                                    return;
                                }
                            }
                            LOGGER.log(Level.WARNING, "unknown client locale {0}", text);
                        }
                        @Override
                        protected void onEncoding(String text) {
                            try {
                                encoding = Charset.forName(text);
                            } catch (UnsupportedCharsetException x) {
                                LOGGER.log(Level.WARNING, "unknown client charset {0}", text);
                            }
                        }
                        @Override
                        protected void onStart() {
                            ready();
                        }
                        @Override
                        protected void onStdin(byte[] chunk) throws IOException {
                            stdinMatch.write(chunk);
                        }
                        @Override
                        protected void onEndStdin() throws IOException {
                            stdinMatch.close();
                        }
                        @Override
                        protected void handleClose() {
                            ready();
                            Thread t = runningThread.get();
                            if (t != null) {
                                t.interrupt();
                            }
                        }
                        private synchronized void ready() {
                            ready = true;
                            notifyAll();
                        }
                    }
                    try (ServerSideImpl connection = new ServerSideImpl(upload, download)) {
                        connection.begin();
                        synchronized (connection) {
                            while (!connection.ready) {
                                connection.wait();
                            }
                        }
                        PrintStream stdout = new PrintStream(connection.streamStdout(), false, connection.encoding.name());
                        PrintStream stderr = new PrintStream(connection.streamStderr(), true, connection.encoding.name());
                        if (connection.args.isEmpty()) {
                            stderr.println("Connection closed before arguments received");
                            connection.sendExit(2);
                            return;
                        }
                        String commandName = connection.args.get(0);
                        CLICommand command = CLICommand.clone(commandName);
                        if (command == null) {
                            stderr.println("No such command " + commandName);
                            connection.sendExit(2);
                            return;
                        }
                        command.setTransportAuth(Jenkins.getAuthentication());
                        command.setClientCharset(connection.encoding);
                        CLICommand orig = CLICommand.setCurrent(command);
                        try {
                            runningThread.set(Thread.currentThread());
                            int exit = command.main(connection.args.subList(1, connection.args.size()), connection.locale, connection.stdin, stdout, stderr);
                            stdout.flush();
                            connection.sendExit(exit);
                            try { // seems to avoid ReadPendingException from Jetty
                                Thread.sleep(1000);
                            } catch (InterruptedException x) {
                                // expected; ignore
                            }
                        } finally {
                            CLICommand.setCurrent(orig);
                            runningThread.set(null);
                        }
                    }
                }
            };
        }
    }

}
