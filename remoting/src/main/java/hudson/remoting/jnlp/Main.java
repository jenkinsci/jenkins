package hudson.remoting.jnlp;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;

import hudson.remoting.Engine;
import hudson.remoting.EngineListener;

/**
 * Entry point to JNLP slave agent.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Main {

    @Option(name="-tunnel",metaVar="HOST:PORT",
            usage="Connect to the specified host and port, instead of connecting directly to Hudson." +
                  "Useful when connection to Hudson needs to be tunneled. Can be also HOST: or :PORT," +
                  "in which case the missing portion will be auto-configured like the default behavior")
    public String tunnel;

    @Option(name="-headless",
            usage="Run in headless mode, without GUI")
    public boolean headlessMode = Boolean.getBoolean("hudson.agent.headless")
                    || Boolean.getBoolean("hudson.webstart.headless");

    /**
     * 4 mandatory parameters.
     * Host name (deprecated), Hudson URL, secret key, and slave name.
     */
    @Argument
    public final List<String> args = new ArrayList<String>();

    public static void main(String[] args) {
        // see http://forum.java.sun.com/thread.jspa?threadID=706976&tstart=0
        // not sure if this is the cause, but attempting to fix
        // https://hudson.dev.java.net/issues/show_bug.cgi?id=310
        // by overwriting the security manager.
        try {
            System.setSecurityManager(null);
        } catch (SecurityException e) {
            // ignore and move on.
            // some user reported that this happens on their JVM: http://d.hatena.ne.jp/tueda_wolf/20080723
        }

        Main m = new Main();
        CmdLineParser p = new CmdLineParser(m);
        try {
            p.parseArgument(args);
            if(m.args.size()!=4)
                throw new CmdLineException("four arguments required");
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("java -jar jnlp-agent.jar [options...] <host> <hudson URL> <secret key> <slave name>");
            p.printUsage(System.err);
            return;
        }

        m.main();
    }

    public void main() {
        Engine engine = new Engine(
                headlessMode ? new CuiListener() : new GuiListener(),
                args.get(0), args.get(1), args.get(2), args.get(3));
        if(tunnel!=null)
            engine.setTunnel(tunnel);
        engine.start();
    }

    /**
     * {@link EngineListener} implementation that sends output to {@link Logger}.
     */
    private static final class CuiListener implements EngineListener {
        private CuiListener() {
            LOGGER.info("Hudson agent is running in headless mode.");
        }

        public void status(final String msg) {
            LOGGER.info(msg);
        }

        public void error(final Throwable t) {
            LOGGER.log(Level.SEVERE, t.getMessage(), t);
            System.exit(-1);
        }

        public void onDisconnect() {
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
}
