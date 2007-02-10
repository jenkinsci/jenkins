package hudson.jnlp;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.Icon;
import javax.swing.JLabel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Dimension;

/**
 * GUI related helper code
 * @author Kohsuke Kawaguchi
 */
public class GUI {
    /**
     * Sets to the platform native look and feel.
     *
     * see http://javaalmanac.com/egs/javax.swing/LookFeelNative.html
     */
    public static void setUILookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (InstantiationException e) {
        } catch (ClassNotFoundException e) {
        } catch (UnsupportedLookAndFeelException e) {
        } catch (IllegalAccessException e) {
        }
    }

    // Set up contraints so that the user supplied component and the
    // background image label overlap and resize identically
    private static final GridBagConstraints gbc;

    static {
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
    }

    public static JPanel wrapInBackgroundImage(JComponent component,
             Icon backgroundIcon,
             int verticalAlignment,
             int horizontalAlignment) {

         // make the passed in swing component transparent
         component.setOpaque(false);

         // create wrapper JPanel
         JPanel backgroundPanel = new JPanel(new GridBagLayout());

         // add the passed in swing component first to ensure that it is in front
         backgroundPanel.add(component, gbc);

         // create a label to paint the background image
         JLabel backgroundImage = new JLabel(backgroundIcon);

         // set minimum and preferred sizes so that the size of the image
         // does not affect the layout size
         backgroundImage.setPreferredSize(new Dimension(1,1));
         backgroundImage.setMinimumSize(new Dimension(1,1));

         // align the image as specified.
         backgroundImage.setVerticalAlignment(verticalAlignment);
         backgroundImage.setHorizontalAlignment(horizontalAlignment);

         // add the background label
         backgroundPanel.add(backgroundImage, gbc);

         // return the wrapper
         return backgroundPanel;
     }
}
