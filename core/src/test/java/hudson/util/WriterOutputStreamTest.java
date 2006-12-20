package hudson.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;

/**
 * TODO: make it a junit test
 * 
 * @author jglick
 */
public class WriterOutputStreamTest {
    public static void main(String[] args) throws IOException {
        OutputStream os = new WriterOutputStream(new OutputStreamWriter(System.out));
        PrintStream ps = new PrintStream(os);
        for (int i = 0; i < 200; i++) {
            ps.println("#" + i + " blah blah blah");
        }
        os.close();
    }
}
