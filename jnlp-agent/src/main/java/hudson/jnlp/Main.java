package hudson.jnlp;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    public static void main(String[] args) {
        // see http://forum.java.sun.com/thread.jspa?threadID=706976&tstart=0
        // not sure if this is the cause, but attempting to fix
        // https://hudson.dev.java.net/issues/show_bug.cgi?id=310
        // by overwriting the security manager.
        System.setSecurityManager(null);

        boolean headlessMode = Boolean.getBoolean("hudson.agent.headless") 
                    || Boolean.getBoolean("hudson.webstart.headless");
        
        if (headlessMode) {
            mainHeadless(args);
        } else {
            mainGui(args);
        }
    }
    private static void mainGui(String[] args) {
        GUI.setUILookAndFeel();
        final MainDialog frame = new MainDialog();
        frame.setVisible(true);

        Engine engine = new Engine(new Listener() {
            public void status(final String msg) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        frame.status(msg);
                    }
                });
            }

            public void error(final Throwable t) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        StringWriter sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw));
                        JOptionPane.showMessageDialog(
                            frame,sw.toString(),"Error",
                            JOptionPane.ERROR_MESSAGE);
                        System.exit(-1);
                    }
                });
            }
        }, args[0], args[1], args[2], args[3]);
        engine.start();
    }
    
    public static void mainHeadless(String[] args) {
        LOGGER.info("Hudson agent is running in headless mode.");
        Engine engine = new Engine(new Listener() {
            public void status(final String msg) {
                LOGGER.info(msg);
            }

            public void error(final Throwable t) {
                LOGGER.severe(t.getMessage());
                System.exit(-1);
            }
        }, args[0], args[1], args[2], args[3]);
        engine.start();
    }
}
