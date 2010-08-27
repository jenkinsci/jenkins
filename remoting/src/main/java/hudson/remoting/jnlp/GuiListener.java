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
package hudson.remoting.jnlp;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import hudson.remoting.EngineListener;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.logging.Logger;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

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
        status(msg,null);
    }

    public void status(final String msg, final Throwable t) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                frame.status(msg);
                if(t!=null)
                    LOGGER.log(INFO, msg, t);
            }
        });
    }

    public void error(final Throwable t) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                LOGGER.log(SEVERE, t.getMessage(), t);
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

    private static final Logger LOGGER = Logger.getLogger(GuiListener.class.getName());
}
