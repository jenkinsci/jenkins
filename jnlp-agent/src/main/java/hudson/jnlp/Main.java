package hudson.jnlp;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {

    public static void main(String[] args) {
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
                        JOptionPane.showMessageDialog(frame,t,"Error",
                            JOptionPane.ERROR_MESSAGE);
                        System.exit(-1);
                    }
                });
            }
        }, args[0], args[1], args[2], args[3]);
        engine.start();
    }
}
