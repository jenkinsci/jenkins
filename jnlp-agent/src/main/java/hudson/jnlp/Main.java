package hudson.jnlp;

import javax.swing.SwingUtilities;

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
        }, args[0], Integer.parseInt(args[1]));
        engine.start();
    }
}
