package hudson.jnlp;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.HeadlessException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MainDialog extends JFrame {

    private JLabel statusLabel;

    public MainDialog() throws HeadlessException {
        super("Hudson slave agent");

        ImageIcon background = new ImageIcon(getClass().getResource("title.png"));

        JPanel foregroundPanel = new JPanel(new BorderLayout(10, 10));
        foregroundPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        foregroundPanel.setOpaque(false);

        statusLabel = new JLabel("",JLabel.TRAILING);
        foregroundPanel.add(statusLabel, BorderLayout.CENTER);

        setContentPane(GUI.wrapInBackgroundImage(foregroundPanel, background,JLabel.BOTTOM,JLabel.LEADING));
        pack();

        setSize(new Dimension(250,150));
        getContentPane().setBackground(Color.WHITE);

        setLocationByPlatform(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    public void status(String msg) {
        statusLabel.setText(msg);
    }
}
