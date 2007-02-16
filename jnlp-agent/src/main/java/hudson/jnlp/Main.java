package hudson.jnlp;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {

    public static void main(String[] args) {
        // see http://forum.java.sun.com/thread.jspa?threadID=706976&tstart=0
        // not sure if this is the cause, but attempting to fix
        // https://hudson.dev.java.net/issues/show_bug.cgi?id=310
        // by overwriting the security manager.
        System.setSecurityManager(null);

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
}
