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
