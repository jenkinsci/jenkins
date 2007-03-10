import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.HeadlessException;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

/**
 * Hudson console GUI.
 * 
 * @author Kohsuke Kawaguchi
 */
class MainDialog extends JFrame {
    private final JTextArea textArea = new JTextArea();

    public MainDialog() throws HeadlessException, IOException {
        super("Hudson Console");
        
        JScrollPane pane = new JScrollPane(textArea);
        pane.setMinimumSize(new Dimension(400,150));
        pane.setPreferredSize(new Dimension(400,150));
        add(pane);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationByPlatform(true);

        PipedOutputStream out = new PipedOutputStream();
        PrintStream pout = new PrintStream(out);
        System.setErr(pout);
        System.setOut(pout);

        // this thread sets the text to JTextArea
        final BufferedReader in = new BufferedReader(new InputStreamReader(new PipedInputStream(out)));
        new Thread() {
            public void run() {
                try {
                    while(true) {
                        String line;
                        while((line=in.readLine())!=null) {
                            final String text = line;
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    textArea.append(text+'\n');
                                    scrollDown();
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }.start();

        pack();
    }

    /**
     * Forces the scroll of text area.
     */
    private void scrollDown() {
        int pos = textArea.getDocument().getEndPosition().getOffset();
        textArea.getCaret().setDot(pos);
        textArea.requestFocus();
    }
}
