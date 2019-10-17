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

import hudson.cli.client.Messages;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.logging.Level.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/**
 * CLI entry point to Jenkins.
 */
public class CLI {

    private CLI() {}

    /**
     * Make sure the connection is open against Jenkins server.
     *
     * @param c The open connection.
     * @throws IOException in case of communication problem.
     * @throws NotTalkingToJenkinsException when connection is not made to Jenkins service.
     */
    /*package*/ static void verifyJenkinsConnection(URLConnection c) throws IOException {
        if (c.getHeaderField("X-Hudson")==null && c.getHeaderField("X-Jenkins")==null)
            throw new NotTalkingToJenkinsException(c);
    }
    /*package*/ static final class NotTalkingToJenkinsException extends IOException {
        public NotTalkingToJenkinsException(String s) {
            super(s);
        }

        public NotTalkingToJenkinsException(URLConnection c) {
            super("There's no Jenkins running at " + c.getURL().toString());
        }
    }

    public static void main(final String[] _args) throws Exception {
        try {
            System.exit(_main(_args));
        } catch (NotTalkingToJenkinsException ex) {
            System.err.println(ex.getMessage());
            System.exit(3);
        } catch (Throwable t) {
            // if the CLI main thread die, make sure to kill the JVM.
            t.printStackTrace();
            System.exit(-1);
        }
    }

    private enum Mode {HTTP, SSH}
    public static int _main(String[] _args) throws Exception {
        List<String> args = Arrays.asList(_args);
        PrivateKeyProvider provider = new PrivateKeyProvider();

        String url = System.getenv("JENKINS_URL");

        if (url==null)
            url = System.getenv("HUDSON_URL");
        
        boolean tryLoadPKey = true;

        Mode mode = null;

        String user = null;
        String auth = null;

        String userIdEnv = System.getenv("JENKINS_USER_ID");
        String tokenEnv = System.getenv("JENKINS_API_TOKEN");

        boolean strictHostKey = false;

        while(!args.isEmpty()) {
            String head = args.get(0);
            if (head.equals("-version")) {
                System.out.println("Version: "+computeVersion());
                return 0;
            }
            if (head.equals("-http")) {
                if (mode != null) {
                    printUsage("-http clashes with previously defined mode " + mode);
                    return -1;
                }
                mode = Mode.HTTP;
                args = args.subList(1, args.size());
                continue;
            }
            if (head.equals("-ssh")) {
                if (mode != null) {
                    printUsage("-ssh clashes with previously defined mode " + mode);
                    return -1;
                }
                mode = Mode.SSH;
                args = args.subList(1, args.size());
                continue;
            }
            if (head.equals("-remoting")) {
                printUsage("-remoting mode is no longer supported");
                return -1;
            }
            if(head.equals("-s") && args.size()>=2) {
                url = args.get(1);
                args = args.subList(2,args.size());
                continue;
            }
            if (head.equals("-noCertificateCheck")) {
                LOGGER.info("Skipping HTTPS certificate checks altogether. Note that this is not secure at all.");
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new NoCheckTrustManager()}, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
                // bypass host name check, too.
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });
                args = args.subList(1,args.size());
                continue;
            }
            if (head.equals("-noKeyAuth")) {
            	tryLoadPKey = false;
            	args = args.subList(1,args.size());
            	continue;
            }
            if(head.equals("-i") && args.size()>=2) {
                File f = new File(args.get(1));
                if (!f.exists()) {
                    printUsage(Messages.CLI_NoSuchFileExists(f));
                    return -1;
                }

                provider.readFrom(f);

                args = args.subList(2,args.size());
                continue;
            }
            if (head.equals("-strictHostKey")) {
                strictHostKey = true;
                args = args.subList(1, args.size());
                continue;
            }
            if (head.equals("-user") && args.size() >= 2) {
                user = args.get(1);
                args = args.subList(2, args.size());
                continue;
            }
            if (head.equals("-auth") && args.size() >= 2) {
                auth = args.get(1);
                args = args.subList(2, args.size());
                continue;
            }
            if (head.equals("-logger") && args.size() >= 2) {
                Level level = parse(args.get(1));
                for (Handler h : Logger.getLogger("").getHandlers()) {
                    h.setLevel(level);
                }
                for (Logger logger : new Logger[] {LOGGER, FullDuplexHttpStream.LOGGER, PlainCLIProtocol.LOGGER, Logger.getLogger("org.apache.sshd")}) { // perhaps also Channel
                    logger.setLevel(level);
                }
                args = args.subList(2, args.size());
                continue;
            }
            break;
        }

        if(url==null) {
            printUsage(Messages.CLI_NoURL());
            return -1;
        }

        if (auth == null) {
            // -auth option not set
            if (StringUtils.isNotBlank(userIdEnv) && StringUtils.isNotBlank(tokenEnv)) {
                auth = StringUtils.defaultString(userIdEnv).concat(":").concat(StringUtils.defaultString(tokenEnv));
            } else if (StringUtils.isNotBlank(userIdEnv) || StringUtils.isNotBlank(tokenEnv)) {
                printUsage(Messages.CLI_BadAuth());
                return -1;
            } // Otherwise, none credentials were set

        }

        if (!url.endsWith("/")) {
            url += '/';
        }

        if(args.isEmpty())
            args = Arrays.asList("help"); // default to help

        if (tryLoadPKey && !provider.hasKeys())
            provider.readFromDefaultLocations();

        if (mode == null) {
            mode = Mode.HTTP;
        }

        LOGGER.log(FINE, "using connection mode {0}", mode);

        if (user != null && auth != null) {
            LOGGER.warning("-user and -auth are mutually exclusive");
        }

        if (mode == Mode.SSH) {
            if (user == null) {
                // TODO SshCliAuthenticator already autodetects the user based on public key; why cannot AsynchronousCommand.getCurrentUser do the same?
                LOGGER.warning("-user required when using -ssh");
                return -1;
            }
            return SSHCLI.sshConnection(url, user, args, provider, strictHostKey);
        }

        if (strictHostKey) {
            LOGGER.warning("-strictHostKey meaningful only with -ssh");
        }

        if (user != null) {
            LOGGER.warning("Warning: -user ignored unless using -ssh");
        }

        CLIConnectionFactory factory = new CLIConnectionFactory();
        String userInfo = new URL(url).getUserInfo();
        if (userInfo != null) {
            factory = factory.basicAuth(userInfo);
        } else if (auth != null) {
            factory = factory.basicAuth(auth.startsWith("@") ? FileUtils.readFileToString(new File(auth.substring(1)), Charset.defaultCharset()).trim() : auth);
        }

        if (mode == Mode.HTTP) {
            return plainHttpConnection(url, args, factory);
        }

        throw new AssertionError();
    }

    private static int plainHttpConnection(String url, List<String> args, CLIConnectionFactory factory) throws IOException, InterruptedException {
        LOGGER.log(FINE, "Trying to connect to {0} via plain protocol over HTTP", url);
        FullDuplexHttpStream streams = new FullDuplexHttpStream(new URL(url), "cli?remoting=false", factory.authorization);
        class ClientSideImpl extends PlainCLIProtocol.ClientSide {
            boolean complete;
            int exit = -1;
            ClientSideImpl(InputStream is, OutputStream os) throws IOException {
                super(is, os);
                if (is.read() != 0) { // cf. FullDuplexHttpService
                    throw new IOException("expected to see initial zero byte; perhaps you are connecting to an old server which does not support -http?");
                }
            }
            @Override
            protected void onExit(int code) {
                this.exit = code;
                finished();
            }
            @Override
            protected void onStdout(byte[] chunk) throws IOException {
                System.out.write(chunk);
            }
            @Override
            protected void onStderr(byte[] chunk) throws IOException {
                System.err.write(chunk);
            }
            @Override
            protected void handleClose() {
                finished();
            }
            private synchronized void finished() {
                complete = true;
                notifyAll();
            }
        }
        try (final ClientSideImpl connection = new ClientSideImpl(streams.getInputStream(), streams.getOutputStream())) {
            for (String arg : args) {
                connection.sendArg(arg);
            }
            connection.sendEncoding(Charset.defaultCharset().name());
            connection.sendLocale(Locale.getDefault().toString());
            connection.sendStart();
            connection.begin();
            new Thread("input reader") {
                @Override
                public void run() {
                    try {
                        final OutputStream stdin = connection.streamStdin();
                        int c;
                        while (!connection.complete && (c = System.in.read()) != -1) {
                           stdin.write(c);
                        }
                        connection.sendEndStdin();
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }.start();
            new Thread("ping") { // JENKINS-46659
                @Override
                public void run() {
                    try {
                        Thread.sleep(PING_INTERVAL);
                        while (!connection.complete) {
                            LOGGER.fine("sending ping");
                            connection.sendEncoding(Charset.defaultCharset().name()); // no-op at this point
                            Thread.sleep(PING_INTERVAL);
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }

            }.start();
            synchronized (connection) {
                while (!connection.complete) {
                    connection.wait();
                }
            }
            return connection.exit;
        }
    }

    private static String computeVersion() {
        Properties props = new Properties();
        try {
            InputStream is = CLI.class.getResourceAsStream("/jenkins/cli/jenkins-cli-version.properties");
            if(is!=null) {
                try {
                    props.load(is);
                } finally {
                    is.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // if the version properties is missing, that's OK.
        }
        return props.getProperty("version","?");
    }

    /**
     * Loads RSA/DSA private key in a PEM format into {@link KeyPair}.
     */
    public static KeyPair loadKey(File f, String passwd) throws IOException, GeneralSecurityException {
        return PrivateKeyProvider.loadKey(f, passwd);
    }

    public static KeyPair loadKey(File f) throws IOException, GeneralSecurityException {
        return loadKey(f, null);
    }

    /**
     * Loads RSA/DSA private key in a PEM format into {@link KeyPair}.
     */
    public static KeyPair loadKey(String pemString, String passwd) throws IOException, GeneralSecurityException {
        return PrivateKeyProvider.loadKey(pemString, passwd);
    }

    public static KeyPair loadKey(String pemString) throws IOException, GeneralSecurityException {
        return loadKey(pemString, null);
    }

    /** For access from {@code HelpCommand}. */
    static String usage() {
        return Messages.CLI_Usage();
    }

    private static void printUsage(String msg) {
        if(msg!=null)   System.out.println(msg);
        System.err.println(usage());
    }

    static final Logger LOGGER = Logger.getLogger(CLI.class.getName());

    private static final int PING_INTERVAL = Integer.getInteger(CLI.class.getName() + ".pingInterval", 3_000); // JENKINS-59267
}
