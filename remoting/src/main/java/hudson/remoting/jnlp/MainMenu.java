package hudson.remoting.jnlp;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Main menu of {@link MainDialog}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MainMenu extends JMenuBar {
    private final MainDialog owner;
    private JMenu fileMenu;

    MainMenu(MainDialog owner) {
        this.owner = owner;
    }

    /**
     * Obtains the file menu (and creates it if necessary),
     * so that the caller can add items in this menu.
     */
    public JMenu getFileMenu() {
        if(fileMenu==null) {
            fileMenu = new JMenu("File");
            fileMenu.setMnemonic(KeyEvent.VK_F);
            add(fileMenu,0);
        }
        return fileMenu;
    }

    /**
     * Reflects the changes made in the menu objects to GUI.
     */
    public void commit() {
        invalidate();
        repaint();
        if(getComponentCount()>0) {
            owner.setJMenuBar(this);
            owner.invalidate();
            owner.repaint();
        }
    }
}
