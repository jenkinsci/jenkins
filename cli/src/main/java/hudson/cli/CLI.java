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

import com.trilead.ssh2.crypto.PEMDecoder;
import hudson.cli.client.Messages;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import hudson.remoting.Pipe;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.Console;

import static java.util.logging.Level.*;

/**
 * CLI entry point to Jenkins.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CLI {
    private final ExecutorService pool;
    private final Channel channel;
    private final CliEntryPoint entryPoint;
    private final boolean ownsPool;
    private final List<Closeable> closables = new ArrayList<Closeable>(); // stuff to close in the close method
    private final String httpsProxyTunnel;
    private final String authorization;

    public CLI(URL jenkins) throws IOException, InterruptedException {
        this(jenkins,null);
    }

    /**
     * @deprecated
     *      Use {@link CLIConnectionFactory} to create {@link CLI}
     */
    public CLI(URL jenkins, ExecutorService exec) throws IOException, InterruptedException {
        this(jenkins,exec,null);
    }

    /**
     * @deprecated 
     *      Use {@link CLIConnectionFactory} to create {@link CLI}
     */
    public CLI(URL jenkins, ExecutorService exec, String httpsProxyTunnel) throws IOException, InterruptedException {
        this(new CLIConnectionFactory().url(jenkins).executorService(exec).httpsProxyTunnel(httpsProxyTunnel));
    }
    
    /*package*/ CLI(CLIConnectionFactory factory) throws IOException, InterruptedException {
        URL jenkins = factory.jenkins;
        this.httpsProxyTunnel = factory.httpsProxyTunnel;
        this.authorization = factory.authorization;
        ExecutorService exec = factory.exec;
        
        String url = jenkins.toExternalForm();
        if(!url.endsWith("/"))  url+='/';

        ownsPool = exec==null;
        pool = exec!=null ? exec : Executors.newCachedThreadPool();

        Channel _channel;
        try {
            _channel = connectViaCliPort(jenkins, getCliTcpPort(url));
        } catch (IOException e) {
            LOGGER.log(Level.FINE,"Failed to connect via CLI port. Falling back to HTTP",e);
            try {
                _channel = connectViaHttp(url);
            } catch (IOException e2) {
                try { // Java 7: e.addSuppressed(e2);
                    Throwable.class.getMethod("addSuppressed", Throwable.class).invoke(e, e2);
                } catch (NoSuchMethodException _ignore) {
                    // Java 6
                } catch (Exception _huh) {
                    LOGGER.log(Level.SEVERE, null, _huh);
                }
                throw e;
            }
        }
        this.channel = _channel;

        // execute the command
        entryPoint = (CliEntryPoint)_channel.waitForRemoteProperty(CliEntryPoint.class.getName());

        if(entryPoint.protocolVersion()!=CliEntryPoint.VERSION)
            throw new IOException(Messages.CLI_VersionMismatch());
    }

    private Channel connectViaHttp(String url) throws IOException {
        LOGGER.log(FINE, "Trying to connect to {0} via HTTP", url);
        url+="cli";
        URL jenkins = new URL(url);

        FullDuplexHttpStream con = new FullDuplexHttpStream(jenkins,authorization);
        Channel ch = new Channel("Chunked connection to "+jenkins,
                pool,con.getInputStream(),con.getOutputStream());
        final long interval = 15*1000;
        final long timeout = (interval * 3) / 4;
        new PingThread(ch,timeout,interval) {
            protected void onDead() {
                // noop. the point of ping is to keep the connection alive
                // as most HTTP servers have a rather short read time out
            }
        }.start();
        return ch;
    }

    private Channel connectViaCliPort(URL jenkins, CliPort clip) throws IOException {
        LOGGER.log(FINE, "Trying to connect directly via TCP/IP to {0}", clip.endpoint);
        final Socket s;
        OutputStream out;

        if (httpsProxyTunnel!=null) {
            String[] tokens = httpsProxyTunnel.split(":");
            s = new Socket(tokens[0], Integer.parseInt(tokens[1]));
            PrintStream o = new PrintStream(s.getOutputStream());
            o.print("CONNECT " + clip.endpoint.getHostName() + ":" + clip.endpoint.getPort() + " HTTP/1.0\r\n\r\n");

            // read the response from the proxy
            ByteArrayOutputStream rsp = new ByteArrayOutputStream();
            while (!rsp.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
                int ch = s.getInputStream().read();
                if (ch<0)   throw new IOException("Failed to read the HTTP proxy response: "+rsp);
                rsp.write(ch);
            }
            String head = new BufferedReader(new StringReader(rsp.toString("ISO-8859-1"))).readLine();
            if (!head.startsWith("HTTP/1.0 200 "))
                throw new IOException("Failed to establish a connection through HTTP proxy: "+rsp);

            // HTTP proxies (at least the one I tried --- squid) doesn't seem to do half-close very well.
            // So instead of relying on it, we'll just send the close command and then let the server
            // cut their side, then close the socket after the join.
            out = new SocketOutputStream(s) {
                @Override
                public void close() throws IOException {
                    // ignore
                }
            };
        } else {
            s = new Socket();
            s.connect(clip.endpoint,3000);
            out = new SocketOutputStream(s);
        }

        closables.add(new Closeable() {
            public void close() throws IOException {
                s.close();
            }
        });

        Connection c = new Connection(new SocketInputStream(s),out);

        switch (clip.version) {
        case 1:
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            dos.writeUTF("Protocol:CLI-connect");
            // we aren't checking greeting from the server here because I'm too lazy. It gets ignored by Channel constructor.
            break;
        case 2:
            DataInputStream dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());
            dos.writeUTF("Protocol:CLI2-connect");
            String greeting = dis.readUTF();
            if (!greeting.equals("Welcome"))
                throw new IOException("Handshaking failed: "+greeting);
            try {
                byte[] secret = c.diffieHellman(false).generateSecret();
                SecretKey sessionKey = new SecretKeySpec(Connection.fold(secret,128/8),"AES");
                c = c.encryptConnection(sessionKey,"AES/CFB8/NoPadding");

                // validate the instance identity, so that we can be sure that we are talking to the same server
                // and there's no one in the middle.
                byte[] signature = c.readByteArray();

                if (clip.identity!=null) {
                    Signature verifier = Signature.getInstance("SHA1withRSA");
                    verifier.initVerify(clip.getIdentity());
                    verifier.update(secret);
                    if (!verifier.verify(signature))
                        throw new IOException("Server identity signature validation failed.");
                }

            } catch (GeneralSecurityException e) {
                throw (IOException)new IOException("Failed to negotiate transport security").initCause(e);
            }
        }

        return new Channel("CLI connection to "+jenkins, pool,
                new BufferedInputStream(c.in), new BufferedOutputStream(c.out));
    }

    /**
     * If the server advertises CLI endpoint, returns its location.
     */
    private CliPort getCliTcpPort(String url) throws IOException {
        URL _url = new URL(url);
        if (_url.getHost()==null || _url.getHost().length()==0) {
            throw new IOException("Invalid URL: "+url);
        }
        URLConnection head = _url.openConnection();
        try {
            head.connect();
        } catch (IOException e) {
            throw (IOException)new IOException("Failed to connect to "+url).initCause(e);
        }

        String h = head.getHeaderField("X-Jenkins-CLI-Host");
        if (h==null)    h = head.getURL().getHost();
        String p1 = head.getHeaderField("X-Jenkins-CLI-Port");
        if (p1==null)    p1 = head.getHeaderField("X-Hudson-CLI-Port");   // backward compatibility
        String p2 = head.getHeaderField("X-Jenkins-CLI2-Port");

        String identity = head.getHeaderField("X-Instance-Identity");

        flushURLConnection(head);
        if (p1==null && p2==null) {
            throw new IOException("No X-Jenkins-CLI2-Port among " + head.getHeaderFields().keySet());
        }

        if (p2!=null)   return new CliPort(new InetSocketAddress(h,Integer.parseInt(p2)),identity,2);
        else            return new CliPort(new InetSocketAddress(h,Integer.parseInt(p1)),identity,1);
    }

    /**
     * Flush the supplied {@link URLConnection} input and close the
     * connection nicely.
     * @param conn the connection to flush/close
     */
    private void flushURLConnection(URLConnection conn) {
        byte[] buf = new byte[1024];
        try {
            InputStream is = conn.getInputStream();
            while (is.read(buf) >= 0) {
                // Ignore
            }
            is.close();
        } catch (IOException e) {
            try {
                InputStream es = ((HttpURLConnection)conn).getErrorStream();
                while (es.read(buf) >= 0) {
                    // Ignore
                }
                es.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    /**
     * Shuts down the channel and closes the underlying connection.
     */
    public void close() throws IOException, InterruptedException {
        channel.close();
        channel.join();
        if(ownsPool)
            pool.shutdown();
        for (Closeable c : closables)
            c.close();
    }

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        return entryPoint.main(args, Locale.getDefault(),
                new RemoteInputStream(stdin),
                new RemoteOutputStream(stdout),
                new RemoteOutputStream(stderr));
    }

    public int execute(List<String> args) {
        return execute(args, System.in, System.out, System.err);
    }

    public int execute(String... args) {
        return execute(Arrays.asList(args));
    }

    /**
     * Returns true if the named command exists.
     */
    public boolean hasCommand(String name) {
        return entryPoint.hasCommand(name);
    }

    /**
     * Accesses the underlying communication channel.
     * @since 1.419
     */
    public Channel getChannel() {
        return channel;
    }

    /**
     * Attempts to lift the security restriction on the underlying channel.
     * This requires the administer privilege on the server.
     *
     * @throws SecurityException
     *      If we fail to upgrade the connection.
     */
    public void upgrade() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (execute(Arrays.asList("groovy", "="),
                new ByteArrayInputStream("hudson.remoting.Channel.current().setRestricted(false)".getBytes()),
                out,out)!=0)
            throw new SecurityException(out.toString()); // failed to upgrade
    }

    public static void main(final String[] _args) throws Exception {
//        Logger l = Logger.getLogger(Channel.class.getName());
//        l.setLevel(ALL);
//        ConsoleHandler h = new ConsoleHandler();
//        h.setLevel(ALL);
//        l.addHandler(h);
//
        System.exit(_main(_args));
    }

    public static int _main(String[] _args) throws Exception {
        List<String> args = Arrays.asList(_args);
        List<KeyPair> candidateKeys = new ArrayList<KeyPair>();
        boolean sshAuthRequestedExplicitly = false;
        String httpProxy=null;

        String url = System.getenv("JENKINS_URL");

        if (url==null)
            url = System.getenv("HUDSON_URL");

        while(!args.isEmpty()) {
            String head = args.get(0);
            if (head.equals("-version")) {
                System.out.println("Version: "+computeVersion());
                return 0;
            }
            if(head.equals("-s") && args.size()>=2) {
                url = args.get(1);
                args = args.subList(2,args.size());
                continue;
            }
            if (head.equals("-noCertificateCheck")) {
                System.out.println("Skipping HTTPS certificate checks altogether. Note that this is not secure at all.");
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
            if(head.equals("-i") && args.size()>=2) {
                File f = new File(args.get(1));
                if (!f.exists()) {
                    printUsage(Messages.CLI_NoSuchFileExists(f));
                    return -1;
                }
                KeyPair kp;
                try {
                    kp = loadKey(f);
                } catch (IOException e) {
                    //if the PEM file is encrypted, IOException is thrown
                    kp = tryEncryptedFile(f);                    
                } catch (GeneralSecurityException e) {
                    throw new Exception("Failed to load key: "+f,e);
                }
                if(kp != null)
                    candidateKeys.add(kp);
                args = args.subList(2,args.size());
                sshAuthRequestedExplicitly = true;
                continue;
            }
            if(head.equals("-p") && args.size()>=2) {
                httpProxy = args.get(1);
                args = args.subList(2,args.size());
                continue;
            }
            break;
        }

        if(url==null) {
            printUsage(Messages.CLI_NoURL());
            return -1;
        }

        if(args.isEmpty())
            args = Arrays.asList("help"); // default to help

        if (candidateKeys.isEmpty())
            addDefaultPrivateKeyLocations(candidateKeys);

        CLIConnectionFactory factory = new CLIConnectionFactory().url(url).httpsProxyTunnel(httpProxy);
        String userInfo = new URL(url).getUserInfo();
        if (userInfo != null) {
            factory = factory.basicAuth(userInfo);
        }

        CLI cli = factory.connect();
        try {
            if (!candidateKeys.isEmpty()) {
                try {
                    // TODO: server verification
                    cli.authenticate(candidateKeys);
                } catch (IllegalStateException e) {
                    if (sshAuthRequestedExplicitly) {
                        System.err.println("The server doesn't support public key authentication");
                        return -1;
                    }
                } catch (UnsupportedOperationException e) {
                    if (sshAuthRequestedExplicitly) {
                        System.err.println("The server doesn't support public key authentication");
                        return -1;
                    }
                } catch (GeneralSecurityException e) {
                    if (sshAuthRequestedExplicitly) {
                        System.err.println(e.getMessage());
                        LOGGER.log(FINE,e.getMessage(),e);
                        return -1;
                    }
                    System.err.println("[WARN] Failed to authenticate with your SSH keys. Proceeding as anonymous");
                    LOGGER.log(FINE,"Failed to authenticate with your SSH keys.",e);
                }
            }

            // execute the command
            // Arrays.asList is not serializable --- see 6835580
            args = new ArrayList<String>(args);
            return cli.execute(args, System.in, System.out, System.err);
        } finally {
            cli.close();
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
        return loadKey(readPemFile(f), passwd);
    }

    public static KeyPair loadKey(File f) throws IOException, GeneralSecurityException {
    	return loadKey(f, null);
    }
    
    private static String readPemFile(File f) throws IOException{
        FileInputStream is = new FileInputStream(f);
        try {
        DataInputStream dis = new DataInputStream(is);
        byte[] bytes = new byte[(int) f.length()];
        dis.readFully(bytes);
        dis.close();
        return new String(bytes);
        } finally {
            is.close();
        }
    }
    
    /**
     * Loads RSA/DSA private key in a PEM format into {@link KeyPair}.
     */
    public static KeyPair loadKey(String pemString, String passwd) throws IOException, GeneralSecurityException {
        Object key = PEMDecoder.decode(pemString.toCharArray(), passwd);
        if (key instanceof com.trilead.ssh2.signature.RSAPrivateKey) {
            com.trilead.ssh2.signature.RSAPrivateKey x = (com.trilead.ssh2.signature.RSAPrivateKey)key;
//            System.out.println("ssh-rsa " + new String(Base64.encode(RSASHA1Verify.encodeSSHRSAPublicKey(x.getPublicKey()))));

            return x.toJCEKeyPair();
        }
        if (key instanceof com.trilead.ssh2.signature.DSAPrivateKey) {
            com.trilead.ssh2.signature.DSAPrivateKey x = (com.trilead.ssh2.signature.DSAPrivateKey)key;
            KeyFactory kf = KeyFactory.getInstance("DSA");
//            System.out.println("ssh-dsa " + new String(Base64.encode(DSASHA1Verify.encodeSSHDSAPublicKey(x.getPublicKey()))));

            return new KeyPair(
                    kf.generatePublic(new DSAPublicKeySpec(x.getY(), x.getP(), x.getQ(), x.getG())),
                    kf.generatePrivate(new DSAPrivateKeySpec(x.getX(), x.getP(), x.getQ(), x.getG())));
        }

        throw new UnsupportedOperationException("Unrecognizable key format: "+key);
    }

    public static KeyPair loadKey(String pemString) throws IOException, GeneralSecurityException {
    	return loadKey(pemString, null);
    }
    
    private static KeyPair tryEncryptedFile(File f) throws IOException, GeneralSecurityException{
        KeyPair kp = null;
        if(isPemEncrypted(f)){
            String passwd = askForPasswd(f.getCanonicalPath());
            kp = loadKey(f,passwd);
        }
        return kp;
    }
    
    private static boolean isPemEncrypted(File f) throws IOException{
        String pemString = readPemFile(f);
        //simple check if the file is encrypted
        return pemString.contains("4,ENCRYPTED");
    }
    
    @SuppressWarnings("Since15")
    @IgnoreJRERequirement
    private static String askForPasswd(String filePath){
        try {
            Console cons = System.console();
            String passwd = null;
            if (cons != null){
                char[] p = cons.readPassword("%s", "Enter passphrase for "+filePath+":");
                passwd = String.valueOf(p);
            }
            return passwd;
        } catch (LinkageError e) {
            throw new Error("Your private key is encrypted, but we need Java6 to ask you password safely",e);
        }
    }
    
    /**
     * try all the default key locations
     */
    private static void addDefaultPrivateKeyLocations(List<KeyPair> keyFileCandidates) {
        File home = new File(System.getProperty("user.home"));
        for (String path : new String[]{".ssh/id_rsa",".ssh/id_dsa",".ssh/identity"}) {
            File key = new File(home,path);
            if (key.exists()) {
                try {
                    keyFileCandidates.add(loadKey(key));
                } catch (IOException e) {
                    // don't report an error. the user can still see it by using the -i option
                    LOGGER.log(FINE, "Failed to load "+key,e);
                } catch (GeneralSecurityException e) {
                    LOGGER.log(FINE, "Failed to load " + key, e);
                }
            }
        }
    }

    /**
     * Authenticate ourselves against the server.
     *
     * @return
     *      identity of the server represented as a public key.
     */
    public PublicKey authenticate(Iterable<KeyPair> privateKeys) throws IOException, GeneralSecurityException {
        Pipe c2s = Pipe.createLocalToRemote();
        Pipe s2c = Pipe.createRemoteToLocal();
        entryPoint.authenticate("ssh",c2s, s2c);
        Connection c = new Connection(s2c.getIn(), c2s.getOut());

        try {
            byte[] sharedSecret = c.diffieHellman(false).generateSecret();
            PublicKey serverIdentity = c.verifyIdentity(sharedSecret);

            // try all the public keys
            for (KeyPair key : privateKeys) {
                c.proveIdentity(sharedSecret,key);
                if (c.readBoolean())
                    return serverIdentity;  // succeeded
            }
            if (privateKeys.iterator().hasNext())
                throw new GeneralSecurityException("Authentication failed. No private key accepted.");
            else
                throw new GeneralSecurityException("No private key is available for use in authentication");
        } finally {
            c.close();
        }
    }

    public PublicKey authenticate(KeyPair key) throws IOException, GeneralSecurityException {
        return authenticate(Collections.singleton(key));
    }

    private static void printUsage(String msg) {
        if(msg!=null)   System.out.println(msg);
        System.err.println(Messages.CLI_Usage());
    }

    private static final Logger LOGGER = Logger.getLogger(CLI.class.getName());
}
