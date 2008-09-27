package hudson.remoting.jnlp;

import hudson.remoting.EngineListener;

import javax.swing.*;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * {@link EngineListener} implementation that shows GUI.
 */
public final class GuiListener implements EngineListener {
    public final MainDialog frame;

    public GuiListener() {
        GUI.setUILookAndFeel();
        frame = new MainDialog();
        frame.setVisible(true);
    }

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

    public void onDisconnect() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // discard all the menu items that might have been added by the master.
                frame.resetMenuBar();
            }
        });
    }
}
