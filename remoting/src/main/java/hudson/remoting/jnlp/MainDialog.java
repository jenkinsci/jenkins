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
