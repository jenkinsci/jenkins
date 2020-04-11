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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.cli.client.Messages;
import java.io.DataInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.logging.Level.*;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;

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

    public static int _main(String[] _args) throws Exception {
        CLIArgsValues argsValues = new CLIArgsValues(_args);

        while(!argsValues.args.isEmpty()) {
          String head = argsValues.args.get(0);

          if (head.equals("-version")) {
            System.out.println("Version: "+computeVersion());
            return 0;
          }

          if (head.equals("-i") && argsValues.args.size()>=2) {
            File f = getFileFromArguments(argsValues.args);
            if (!f.exists()) {
              return printAndReturn(Messages.CLI_NoSuchFileExists(f));
            }

            argsValues.provider.readFrom(f);
            argsValues.args = argsValues.args.subList(2, argsValues.args.size());
          } 

          if (argsValues.mode != null && (head.equals("-http") || head.equals("-ssh") || head.equals("-webSocket"))) {
            return printAndReturn(head + " clashes with previously defined mode " + argsValues.mode);
          }
          
          if (head.equals("-remoting")) {
            return printAndReturn("-remoting mode is no longer supported");
          }

          if (argsValues.isValidArg(head)) {
            argsValues.parseAndSetArgs(head, argsValues);
          } 
          // so we don't loop forever in case they don't provide a valid arg
          else {
            break;
          }
        }

        if(argsValues.url==null) {
          return printAndReturn(Messages.CLI_NoURL());
        }

        if (argsValues.auth == null) {
          // -auth option not set
          if (argsValues.hasTokenEnvValues(argsValues.userIdEnv, argsValues.tokenEnv)) {
            argsValues.auth = argsValues.createAuthToken(argsValues.userIdEnv, argsValues.tokenEnv);
          } else if (StringUtils.isNotBlank(argsValues.userIdEnv) || StringUtils.isNotBlank(argsValues.tokenEnv)) {
            return printAndReturn(Messages.CLI_BadAuth());
          } // Otherwise, none credentials were set
        }

        if (!argsValues.url.endsWith("/")) {
          argsValues.url += '/';
        }

        if(argsValues.args.isEmpty())
          argsValues.args = Arrays.asList("help"); // default to help

        if (argsValues.mode == null) {
          argsValues.mode = CLIArgsValues.Mode.HTTP;
        }

        LOGGER.log(FINE, "using connection mode {0}", argsValues.mode);

        if (argsValues.user != null && argsValues.auth != null) {
            LOGGER.warning("-user and -auth are mutually exclusive");
        }

        if (argsValues.mode == CLIArgsValues.Mode.SSH) {
            if (argsValues.user == null) {
                // TODO SshCliAuthenticator already autodetects the user based on public key; why cannot AsynchronousCommand.getCurrentUser do the same?
                LOGGER.warning("-user required when using -ssh");
                return -1;
            }
            if (!argsValues.noKeyAuth && !argsValues.provider.hasKeys()) {
              argsValues.provider.readFromDefaultLocations();
            }
            return SSHCLI.sshConnection(argsValues.url, argsValues.user, argsValues.args, argsValues.provider, argsValues.strictHostKey);
        }

        if (argsValues.strictHostKey) {
            LOGGER.warning("-strictHostKey meaningful only with -ssh");
        }

        if (argsValues.noKeyAuth) {
            LOGGER.warning("-noKeyAuth meaningful only with -ssh");
        }

        if (argsValues.user != null) {
            LOGGER.warning("Warning: -user ignored unless using -ssh");
        }

        CLIConnectionFactory factory = new CLIConnectionFactory();
        String userInfo = new URL(argsValues.url).getUserInfo();
        if (userInfo != null) {
            factory = factory.basicAuth(userInfo);
        } else if (argsValues.auth != null) {
            factory = factory.basicAuth(argsValues.auth.startsWith("@") ? readAuthFromFile(argsValues.auth).trim() : argsValues.auth);
        }

        if (argsValues.mode == CLIArgsValues.Mode.HTTP) {
            return plainHttpConnection(argsValues.url, argsValues.args, factory);
        }

        if (argsValues.mode == CLIArgsValues.Mode.WEB_SOCKET) {
            return webSocketConnection(argsValues.url, argsValues.args, factory);
        }

        throw new AssertionError();
    }

    private static int printAndReturn(String message) {
      printUsage(message);
      return -1;
    }

    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "URLCONNECTION_SSRF_FD"}, justification = "User provided values for running the program.")
    private static String readAuthFromFile(String auth) throws IOException {
        return FileUtils.readFileToString(new File(auth.substring(1)), Charset.defaultCharset());
    }

    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "URLCONNECTION_SSRF_FD"}, justification = "User provided values for running the program.")
    private static File getFileFromArguments(List<String> args) {
        return new File(args.get(1));
    }

    private static int webSocketConnection(String url, List<String> args, CLIConnectionFactory factory) throws Exception {
        LOGGER.fine(() -> "Trying to connect to " + url + " via plain protocol over WebSocket");
        class CLIEndpoint extends Endpoint {
            @Override
            public void onOpen(Session session, EndpointConfig config) {}
        }
        class Authenticator extends ClientEndpointConfig.Configurator {
            @Override
            public void beforeRequest(Map<String, List<String>> headers) {
                if (factory.authorization != null) {
                    headers.put("Authorization", Collections.singletonList(factory.authorization));
                }
            }
        }
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName()); // ~ ContainerProvider.getWebSocketContainer()
        client.getProperties().put(ClientProperties.REDIRECT_ENABLED, true); // https://tyrus-project.github.io/documentation/1.13.1/index/tyrus-proprietary-config.html#d0e1775
        Session session = client.connectToServer(new CLIEndpoint(), ClientEndpointConfig.Builder.create().configurator(new Authenticator()).build(), URI.create(url.replaceFirst("^http", "ws") + "cli/ws"));
        PlainCLIProtocol.Output out = new PlainCLIProtocol.Output() {
            @Override
            public void send(byte[] data) throws IOException {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
            }
            @Override
            public void close() throws IOException {
                session.close();
            }
        };
        try (ClientSideImpl connection = new ClientSideImpl(out)) {
            session.addMessageHandler(InputStream.class, is -> {
                try {
                    connection.handle(new DataInputStream(is));
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            });
            connection.start(args);
            return connection.exit();
        }
    }

    private static int plainHttpConnection(String url, List<String> args, CLIConnectionFactory factory) throws IOException, InterruptedException {
        LOGGER.log(FINE, "Trying to connect to {0} via plain protocol over HTTP", url);
        FullDuplexHttpStream streams = new FullDuplexHttpStream(new URL(url), "cli?remoting=false", factory.authorization);
        try (final ClientSideImpl connection = new ClientSideImpl(new PlainCLIProtocol.FramedOutput(streams.getOutputStream()))) {
            connection.start(args);
            InputStream is = streams.getInputStream();
            if (is.read() != 0) { // cf. FullDuplexHttpService
                throw new IOException("expected to see initial zero byte; perhaps you are connecting to an old server which does not support -http?");
            }
            new PlainCLIProtocol.FramedReader(connection, is).start();
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
            return connection.exit();
        }
    }

    private static final class ClientSideImpl extends PlainCLIProtocol.ClientSide {

        volatile boolean complete;
        private int exit = -1;

        ClientSideImpl(PlainCLIProtocol.Output out) {
            super(out);
        }

        void start(List<String> args) throws IOException {
            for (String arg : args) {
                sendArg(arg);
            }
            sendEncoding(Charset.defaultCharset().name());
            sendLocale(Locale.getDefault().toString());
            sendStart();
            new Thread("input reader") {
                @Override
                public void run() {
                    try {
                        final OutputStream stdin = streamStdin();
                        int c;
                        // TODO check available to avoid sending lots of one-byte frames
                        while (!complete && (c = System.in.read()) != -1) {
                           stdin.write(c);
                        }
                        sendEndStdin();
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }.start();
        }

        @Override
        protected synchronized void onExit(int code) {
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

        synchronized int exit() throws InterruptedException {
            while (!complete) {
                wait();
            }
            return exit;
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

class CLIArgsValues {
  List<String> args;

  PrivateKeyProvider provider;

  String url;

  boolean noKeyAuth;

  public static enum Mode {HTTP, SSH, WEB_SOCKET}
  Mode mode;
  
  String user;
  String auth;
  
  String userIdEnv;
  String tokenEnv;
  
  boolean strictHostKey;

  public CLIArgsValues(final String[] _args) {
    args = Arrays.asList(_args);
    provider = new PrivateKeyProvider();

    url = ((System.getenv("JENKINS_URL") == null) ? System.getenv("HUDSON_URL") : System.getenv("JENKINS_URL"));

    noKeyAuth = false;

    mode = null;

    user = null;
    auth = null;

    userIdEnv = System.getenv("JENKINS_USER_ID");
    tokenEnv = System.getenv("JENKINS_API_TOKEN");
    
    strictHostKey = false;
  }

  public static Mode setMode(String head) {
    if (head.equals("-http")) {
      return Mode.HTTP;
    } else if(head.equals("-ssh")) {
      return Mode.SSH;
    } else {
      return Mode.WEB_SOCKET;
    }
  }

  public boolean isValidArg(String head) {
    if (head.equals("-http") || 
        head.equals("-ssh") || 
        head.equals("-webSocket") || 
        head.equals("-s") || 
        head.equals("-noCertificateCheck") || 
        head.equals("-noKeyAuth") ||
        head.equals("-strictHostKey") ||
        head.equals("-user") ||
        head.equals("-auth") ||
        head.equals("-logger")) {
          return true;
        }

    return false;
  }

  public void parseAndSetArgs(String head, CLIArgsValues argsValues) throws Exception {
    if (head.equals("-http") || head.equals("-ssh") || head.equals("-webSocket")) {
      argsValues.mode = CLIArgsValues.setMode(head);
      argsValues.args = argsValues.args.subList(1, argsValues.args.size());
    }
    else if(head.equals("-s") && args.size()>=2) {
      argsValues.url = argsValues.args.get(1);
      argsValues.args = argsValues.args.subList(2, argsValues.args.size());
    }
    else if (head.equals("-noCertificateCheck")) {
      argsValues.noCertificateCheck();
      argsValues.args = argsValues.args.subList(1,argsValues.args.size());
    }
    else if (head.equals("-noKeyAuth")) {
      argsValues.noKeyAuth = true;
      argsValues.args = argsValues.args.subList(1,argsValues.args.size());
    }
    else if (head.equals("-strictHostKey")) {
      argsValues.strictHostKey = true;
      argsValues.args = argsValues.args.subList(1, argsValues.args.size());
    }
    else if (head.equals("-user") && args.size() >= 2) {
      argsValues.user = argsValues.args.get(1);
      argsValues.args = argsValues.args.subList(2, argsValues.args.size());
    }
    else if (head.equals("-auth") && args.size() >= 2) {
      argsValues.auth = argsValues.args.get(1);
      argsValues.args = argsValues.args.subList(2, argsValues.args.size());
    }
    else if (head.equals("-logger") && args.size() >= 2) {
      Level level = parse(args.get(1));
      setHandlerLevel(level);
      setLoggerLevel(level);

      argsValues.args = argsValues.args.subList(2, args.size());
    }
  }

  public Boolean hasTokenEnvValues(String userIdEnv, String tokenEnv) {
    return StringUtils.isNotBlank(userIdEnv) && StringUtils.isNotBlank(tokenEnv);
  }

  public String createAuthToken(String userIdEnv, String tokenEnv) {
    return StringUtils.defaultString(userIdEnv).concat(":").concat(StringUtils.defaultString(tokenEnv));
  }

  private void noCertificateCheck() throws Exception {
    CLI.LOGGER.info("Skipping HTTPS certificate checks altogether. Note that this is not secure at all.");
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[]{new NoCheckTrustManager()}, new SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    // bypass host name check, too.
    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
      @SuppressFBWarnings(value = "WEAK_HOSTNAME_VERIFIER", justification = "User set parameter to skip verifier.")
      public boolean verify(String s, SSLSession sslSession) {
          return true;
      }
    });
  }

  private void setHandlerLevel(Level level) {
    for (Handler h : Logger.getLogger("").getHandlers()) {
      h.setLevel(level);
    }
  }

  private void setLoggerLevel(Level level) {
    for (Logger logger : new Logger[] {CLI.LOGGER, FullDuplexHttpStream.LOGGER, PlainCLIProtocol.LOGGER, Logger.getLogger("org.apache.sshd")}) { // perhaps also Channel
      logger.setLevel(level);
    }
  }
}
