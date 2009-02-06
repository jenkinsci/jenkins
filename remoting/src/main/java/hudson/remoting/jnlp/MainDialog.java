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

import hudson.remoting.Engine;

import javax.swing.*;
import java.awt.*;

/**
 * Main window for JNLP slave agent.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MainDialog extends JFrame {

    private MainMenu mainMenu;
    private final JLabel statusLabel;

    public MainDialog() throws HeadlessException {
        super("Hudson slave agent");

        ImageIcon background = new ImageIcon(getClass().getResource("title.png"));

        JPanel foregroundPanel = new JPanel(new BorderLayout(10, 10));
        foregroundPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        foregroundPanel.setOpaque(false);

        statusLabel = new JLabel("",JLabel.TRAILING);
        foregroundPanel.add(statusLabel, BorderLayout.CENTER);

        setContentPane(GUI.wrapInBackgroundImage(foregroundPanel, background,JLabel.BOTTOM,JLabel.LEADING));
        resetMenuBar();

        pack();

        setSize(new Dimension(250,150));
        getContentPane().setBackground(Color.WHITE);

        setLocationByPlatform(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    /**
     * Gets the main menu of this window, so that the caller can add
     * additional menu items.
     *
     * @return never null.
     */
    public MainMenu getMainMenu() {
        return mainMenu;
    }

    public void resetMenuBar() {
        mainMenu = new MainMenu(this);
        if(mainMenu.getComponentCount()>0) {
            setJMenuBar(mainMenu);
            mainMenu.commit();
        } else {
            setJMenuBar(null);
            if(isVisible())
                setVisible(true); // work around for paint problem. see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4949810
        }
    }

    public void status(String msg) {
        statusLabel.setText(msg);
    }

    /**
     * If the current JVM runs a {@link MainDialog} as a JNLP slave agent,
     * return its reference, otherwise null.
     */
    public static MainDialog get() {
        Engine e = Engine.current();
        if(e==null)     return null;
        if (!(e.listener instanceof GuiListener))   return null;
        return ((GuiListener) e.listener).frame;
    }
}
