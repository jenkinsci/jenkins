/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;

import hudson.remoting.Channel.Mode;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

/**
 * Entry point for running a {@link Channel}. This is the main method of the slave JVM.
 *
 * <p>
 * This class also defines several methods for
 * starting a channel on a fresh JVM.
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {
    public static void main(String[] args) throws Exception {
        Mode m = Mode.BINARY;
        boolean ping = false;
        URL slaveJnlpURL = null;
        File tcpPortFile = null;

        for(int i=0; i<args.length; i++) {
            String arg = args[i];
            if(arg.equals("-text")) {
                System.out.println("Running in text mode");
                m = Mode.TEXT;
                continue;
            }
            if(arg.equals("-ping")) {
                ping = true;
                continue;
            }
            if(arg.equals("-jnlpUrl")) {
                if(i+1==args.length) {
                    System.err.println("The -jnlpUrl option is missing a URL parameter");
                    System.exit(1);
                }
                slaveJnlpURL = new URL(args[++i]);
                continue;
            }
            if(arg.equals("-tcp")) {
                if(i+1==args.length) {
                    System.err.println("The -tcp option is missing a file name parameter");
                    System.exit(1);
                }
                tcpPortFile = new File(args[++i]);
                continue;
            }
            if(arg.equals("-noCertificateCheck")) {
                // bypass HTTPS security check by using free-for-all trust manager
                System.out.println("Skipping HTTPS certificate checks altoghether. Note that this is not secure at all.");
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new NoCheckTrustManager()}, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
                // bypass host name check, too.
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });
                continue;
            }
            System.err.println("Invalid option: "+arg);
            System.exit(-1);
        }


        if(slaveJnlpURL!=null) {
            List<String> jnlpArgs = parseJnlpArguments(slaveJnlpURL);
            hudson.remoting.jnlp.Main.main(jnlpArgs.toArray(new String[jnlpArgs.size()]));
        } else
        if(tcpPortFile!=null) {
            runAsTcpServer(tcpPortFile,m,ping);
            System.exit(0);
        } else {
            runWithStdinStdout(m, ping);
            System.exit(0);
        }
    }

    /**
     * Parses the connection arguments from JNLP file given in the URL.
     */
    private static List<String> parseJnlpArguments(URL slaveJnlpURL) throws ParserConfigurationException, SAXException, IOException, InterruptedException {
        while (true) {
            try {
                URLConnection con = slaveJnlpURL.openConnection();
                con.connect();

                // check if this URL points to a .jnlp file
                String contentType = con.getHeaderField("Content-Type");
                if(contentType==null || !contentType.startsWith("application/x-java-jnlp-file"))
                        throw new IOException(slaveJnlpURL+" doesn't look like a JNLP file");

                // exec into the JNLP launcher, to fetch the connection parameter through JNLP.
                DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document dom = db.parse(con.getInputStream(),slaveJnlpURL.toExternalForm());
                NodeList argElements = dom.getElementsByTagName("argument");
                List<String> jnlpArgs = new ArrayList<String>();
                for( int i=0; i<argElements.getLength(); i++ )
                        jnlpArgs.add(argElements.item(i).getTextContent());
                // force a headless mode
                jnlpArgs.add("-headless");
                return jnlpArgs;
            } catch (SSLHandshakeException e) {
                if(e.getMessage().contains("PKIX path building failed")) {
                    // invalid SSL certificate. One reason this happens is when the certificate is self-signed
                    IOException x = new IOException("Failed to validate a server certificate. If you are using a self-signed certificate, you can use the -noCertificateCheck option to bypass this check.");
                    x.initCause(e);
                    throw x;
                } else
                    throw e;
            } catch (IOException e) {
                System.err.println("Failing to obtain "+slaveJnlpURL);
                e.printStackTrace(System.err);
                System.err.println("Waiting 10 seconds before retry");
                Thread.sleep(10*1000);
                // retry
            }
        }
    }

    /**
     * Listens on an ephemeral port, record that port number in a port file,
     * then accepts one TCP connection.
     */
    private static void runAsTcpServer(File portFile, Mode m, boolean ping) throws IOException, InterruptedException {
        // if no one connects for too long, assume something went wrong
        // and avoid hanging foreever
        ServerSocket ss = new ServerSocket(0,1);
        ss.setSoTimeout(30*1000);

        // write a port file to report the port number
        FileWriter w = new FileWriter(portFile);
        w.write(String.valueOf(ss.getLocalPort()));
        w.close();

        // accept just one connection and that's it.
        // when we are done, remove the port file to avoid stale port file
        Socket s;
        try {
            s = ss.accept();
            ss.close();
        } finally {
            portFile.delete();
        }

        main(new BufferedInputStream(new SocketInputStream(s)),
             new BufferedOutputStream(new SocketOutputStream(s)),m,ping);
    }

    private static void runWithStdinStdout(Mode m, boolean ping) throws IOException, InterruptedException {
        // use stdin/stdout for channel communication
        ttyCheck();

        // this will prevent programs from accidentally writing to System.out
        // and messing up the stream.
        OutputStream os = System.out;
        System.setOut(System.err);
        main(System.in,os,m,ping);
    }

    private static void ttyCheck() {
        try {
            Method m = System.class.getMethod("console");
            Object console = m.invoke(null);
            if(console!=null) {
                // we seem to be running from interactive console. issue a warning.
                // but since this diagnosis could be wrong, go on and do what we normally do anyway. Don't exit.
                System.out.println(
                        "WARNING: Are you running slave agent from an interactive console?\n" +
                        "If so, you are probably using it incorrectly.\n" +
                        "See http://hudson.gotdns.com/wiki/display/HUDSON/Launching+slave.jar+from+from+console");
            }
        } catch (LinkageError e) {
            // we are probably running on JDK5 that doesn't have System.console()
            // we can't check
        } catch (InvocationTargetException e) {
            // this is impossible
            throw new AssertionError(e);
        } catch (NoSuchMethodException e) {
            // must be running on JDK5
        } catch (IllegalAccessException e) {
            // this is impossible
            throw new AssertionError(e);
        }
    }

    public static void main(InputStream is, OutputStream os) throws IOException, InterruptedException {
        main(is,os,Mode.BINARY);
    }

    public static void main(InputStream is, OutputStream os, Mode mode) throws IOException, InterruptedException {
        main(is,os,mode,false);
    }

    public static void main(InputStream is, OutputStream os, Mode mode, boolean performPing) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        Channel channel = new Channel("channel", executor, mode, is, os);
        System.err.println("channel started");
        if(performPing) {
            System.err.println("Starting periodic ping thread");
            new PingThread(channel) {
                @Override
                protected void onDead() {
                    System.err.println("Ping failed. Terminating");
                    System.exit(-1);
                }
            }.start();
        }
        channel.join();
        System.err.println("channel stopped");
    }

    /**
     * {@link X509TrustManager} that performs no check at all.
     */
    private static class NoCheckTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
