/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.console;

import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.MarkupText;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import hudson.model.Run;
import hudson.remoting.ObjectInputStreamEx;
import hudson.util.IOUtils;
import hudson.util.UnbufferedBase64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.tools.ant.BuildListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import com.jcraft.jzlib.GZIPInputStream;
import com.jcraft.jzlib.GZIPOutputStream;

/**
 * Data that hangs off from a console output.
 *
 * <p>
 * A {@link ConsoleNote} can be put into a console output while it's being written, and it represents
 * a machine readable information about a particular position of the console output.
 *
 * <p>
 * When Hudson is reading back a console output for display, a {@link ConsoleNote} is used
 * to trigger {@link ConsoleAnnotator}, which in turn uses the information in the note to
 * generate markup. In this way, we can overlay richer information on top of the console output.
 *
 * <h2>Comparison with {@link ConsoleAnnotatorFactory}</h2>
 * <p>
 * Compared to {@link ConsoleAnnotatorFactory}, the main advantage of {@link ConsoleNote} is that
 * it can be emitted into the output by the producer of the output (or by a filter), which can
 * have a much better knowledge about the context of what's being executed.
 *
 * <ol>
 * <li>
 * For example, when your plugin is about to report an error message, you can emit a {@link ConsoleNote}
 * that indicates an error, instead of printing an error message as plain text. The {@link #annotate(Object, MarkupText, int)}
 * method will then generate the proper error message, with all the HTML markup that makes error message
 * more user friendly.
 *
 * <li>
 * Or consider annotating output from Ant. A modified {@link BuildListener} can place a {@link ConsoleNote}
 * every time a new target execution starts. These notes can be then later used to build the outline
 * that shows what targets are executed, hyperlinked to their corresponding locations in the build output.
 * </ol>
 *
 * <p>
 * Doing these things by {@link ConsoleAnnotatorFactory} would be a lot harder, as they can only rely
 * on the pattern matching of the output.
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link ConsoleNote}s are serialized and gzip compressed into a byte sequence and then embedded into the
 * console output text file, with a bit of preamble/postamble to allow tools to ignore them. In this way
 * {@link ConsoleNote} always sticks to a particular point in the console output.
 *
 * <p>
 * This design allows descendant processes of Hudson to emit {@link ConsoleNote}s. For example, Ant forked
 * by a shell forked by Hudson can put an encoded note in its stdout, and Hudson will correctly understands that.
 * The preamble and postamble includes a certain ANSI escape sequence designed in such a way to minimize garbage
 * if this output is observed by a human being directly.
 *
 * <p>
 * Because of this persistence mechanism, {@link ConsoleNote}s need to be serializable, and care should be taken
 * to reduce footprint of the notes, if you are putting a lot of notes. Serialization format compatibility
 * is also important, although {@link ConsoleNote}s that failed to deserialize will be simply ignored, so the
 * worst thing that can happen is that you just lose some notes.
 *
 * <h2>Behaviour, JavaScript, and CSS</h2>
 * <p>
 * {@link ConsoleNote} can have associated <tt>script.js</tt> and <tt>style.css</tt> (put them
 * in the same resource directory that you normally put Jelly scripts), which will be loaded into
 * the HTML page whenever the console notes are used. This allows you to use minimal markup in
 * code generation, and do the styling in CSS and perform the rest of the interesting work as a CSS behaviour/JavaScript.
 *
 * @param <T>
 *      Contextual model object that this console is associated with, such as {@link Run}.
 *
 * @author Kohsuke Kawaguchi
 * @see ConsoleAnnotationDescriptor
 * @see Functions#generateConsoleAnnotationScriptAndStylesheet()
 * @since 1.349
 */
public abstract class ConsoleNote<T> implements Serializable, Describable<ConsoleNote<?>>, ExtensionPoint {
    /**
     * When the line of a console output that this annotation is attached is read by someone,
     * a new {@link ConsoleNote} is de-serialized and this method is invoked to annotate that line.
     *
     * @param context
     *      The object that owns the console output in question.
     * @param text
     *      Represents a line of the console output being annotated.
     * @param charPos
     *      The character position in 'text' where this annotation is attached.
     *
     * @return
     *      if non-null value is returned, this annotator will handle the next line.
     *      this mechanism can be used to annotate multiple lines starting at the annotated position. 
     */
    public abstract ConsoleAnnotator annotate(T context, MarkupText text, int charPos);

    public ConsoleAnnotationDescriptor getDescriptor() {
        return (ConsoleAnnotationDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Prints this note into a stream.
     *
     * <p>
     * The most typical use of this is {@code n.encodedTo(System.out)} where stdout is connected to Hudson.
     * The encoded form doesn't include any new line character to work better in the line-oriented nature
     * of {@link ConsoleAnnotator}.
     */
    public void encodeTo(OutputStream out) throws IOException {
        // atomically write to the final output, to minimize the chance of something else getting in between the output.
        // even with this, it is still technically possible to get such a mix-up to occur (for example,
        // if Java program is reading stdout/stderr separately and copying them into the same final stream.)
        out.write(encodeToBytes().toByteArray());
    }

    /**
     * Prints this note into a writer.
     *
     * <p>
     * Technically, this method only works if the {@link Writer} to {@link OutputStream}
     * encoding is ASCII compatible.
     */
    public void encodeTo(Writer out) throws IOException {
        out.write(encodeToBytes().toString());
    }

    private ByteArrayOutputStream encodeToBytes() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(buf));
        try {
            oos.writeObject(this);
        } finally {
            oos.close();
        }

        ByteArrayOutputStream buf2 = new ByteArrayOutputStream();

        DataOutputStream dos = new DataOutputStream(new Base64OutputStream(buf2,true,-1,null));
        try {
            buf2.write(PREAMBLE);
            dos.writeInt(buf.size());
            buf.writeTo(dos);
        } finally {
            dos.close();
        }
        buf2.write(POSTAMBLE);
        return buf2;
    }

    /**
     * Works like {@link #encodeTo(Writer)} but obtain the result as a string.
     */
    public String encode() throws IOException {
        return encodeToBytes().toString();
    }

    /**
     * Reads a note back from {@linkplain #encodeTo(OutputStream) its encoded form}.
     *
     * @param in
     *      Must point to the beginning of a preamble.
     *
     * @return null if the encoded form is malformed.
     */
    public static ConsoleNote readFrom(DataInputStream in) throws IOException, ClassNotFoundException {
        try {
            byte[] preamble = new byte[PREAMBLE.length];
            in.readFully(preamble);
            if (!Arrays.equals(preamble,PREAMBLE))
                return null;    // not a valid preamble

            DataInputStream decoded = new DataInputStream(new UnbufferedBase64InputStream(in));
            int sz = decoded.readInt();
            byte[] buf = new byte[sz];
            decoded.readFully(buf);

            byte[] postamble = new byte[POSTAMBLE.length];
            in.readFully(postamble);
            if (!Arrays.equals(postamble,POSTAMBLE))
                return null;    // not a valid postamble

            ObjectInputStream ois = new ObjectInputStreamEx(
                    new GZIPInputStream(new ByteArrayInputStream(buf)), Jenkins.getInstance().pluginManager.uberClassLoader);
            try {
                return (ConsoleNote) ois.readObject();
            } finally {
                ois.close();
            }
        } catch (Error e) {
            // for example, bogus 'sz' can result in OutOfMemoryError.
            // package that up as IOException so that the caller won't fatally die.
            throw new IOException(e);
        }
    }

    /**
     * Skips the encoded console note.
     */
    public static void skip(DataInputStream in) throws IOException {
        byte[] preamble = new byte[PREAMBLE.length];
        in.readFully(preamble);
        if (!Arrays.equals(preamble,PREAMBLE))
            return;    // not a valid preamble

        DataInputStream decoded = new DataInputStream(new UnbufferedBase64InputStream(in));
        int sz = decoded.readInt();
        IOUtils.skip(decoded,sz);

        byte[] postamble = new byte[POSTAMBLE.length];
        in.readFully(postamble);
    }

    private static final long serialVersionUID = 1L;

    public static final String PREAMBLE_STR = "\u001B[8mha:";
    public static final String POSTAMBLE_STR = "\u001B[0m";

    /**
     * Preamble of the encoded form. ANSI escape sequence to stop echo back
     * plus a few magic characters.
     */
    public static final byte[] PREAMBLE = PREAMBLE_STR.getBytes();
    /**
     * Post amble is the ANSI escape sequence that brings back the echo.
     */
    public static final byte[] POSTAMBLE = POSTAMBLE_STR.getBytes();

    /**
     * Locates the preamble in the given buffer.
     */
    public static int findPreamble(byte[] buf, int start, int len) {
        int e = start + len - PREAMBLE.length + 1;

        OUTER:
        for (int i=start; i<e; i++) {
            if (buf[i]==PREAMBLE[0]) {
                // check for the rest of the match
                for (int j=1; j<PREAMBLE.length; j++) {
                    if (buf[i+j]!=PREAMBLE[j])
                        continue OUTER;
                }
                return i; // found it
            }
        }
        return -1; // not found
    }

    /**
     * Removes the embedded console notes in the given log lines.
     *
     * @since 1.350
     */
    public static List<String> removeNotes(Collection<String> logLines) {
        List<String> r = new ArrayList<String>(logLines.size());
        for (String l : logLines)
            r.add(removeNotes(l));
        return r;
    }

    /**
     * Removes the embedded console notes in the given log line.
     *
     * @since 1.350
     */
    public static String removeNotes(String line) {
        while (true) {
            int idx = line.indexOf(PREAMBLE_STR);
            if (idx<0)  return line;
            int e = line.indexOf(POSTAMBLE_STR,idx);
            if (e<0)    return line;
            line = line.substring(0,idx)+line.substring(e+POSTAMBLE_STR.length());
        }
    }
}
