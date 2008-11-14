package hudson.remoting;

import hudson.remoting.Channel.Mode;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for running a {@link Channel}.
 *
 * <p>
 * This can be used as the main class for launching a channel on
 * a separate JVM.
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {
    public static void main(String[] args) throws Exception {
        Mode m = Mode.BINARY;
        boolean ping = false;
        URL slaveJnlpURL = null;

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
            System.err.println("Invalid option: "+arg);
            System.exit(-1);
        }


        if(slaveJnlpURL!=null) {
            List<String> jnlpArgs = parseJnlpArguments(slaveJnlpURL);
            hudson.remoting.jnlp.Main.main(jnlpArgs.toArray(new String[jnlpArgs.size()]));
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
                HttpURLConnection con = (HttpURLConnection) slaveJnlpURL.openConnection();
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
            } catch (IOException e) {
                System.err.println("Failing to obtain "+slaveJnlpURL);
                e.printStackTrace(System.err);
                System.err.println("Waiting 10 seconds before retry");
                Thread.sleep(10*1000);
                // retry
            }
        }
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
}
