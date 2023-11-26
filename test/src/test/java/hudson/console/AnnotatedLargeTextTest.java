/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.Assert.assertEquals;

import hudson.MarkupText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.stapler.framework.io.ByteBuffer;

@For({AnnotatedLargeText.class, ConsoleNote.class, ConsoleAnnotationOutputStream.class, PlainTextConsoleOutputStream.class})
public class AnnotatedLargeTextTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ConsoleAnnotationOutputStream.class, Level.FINE).record(PlainTextConsoleOutputStream.class, Level.FINE).capture(100);

    @Test
    public void smokes() throws Exception {
        ByteBuffer buf = new ByteBuffer();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        ps.print("Some text.\n");
        ps.print("Go back to " + TestNote.encodeTo("/root", "your home") + ".\n");
        ps.print("More text.\n");
        AnnotatedLargeText<Void> text = new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        text.writeLogTo(0, baos);
        assertEquals("Some text.\nGo back to your home.\nMore text.\n", baos.toString(StandardCharsets.UTF_8));
        StringWriter w = new StringWriter();
        text.writeHtmlTo(0, w);
        assertEquals("Some text.\nGo back to <a href='/root'>your home</a>.\nMore text.\n", w.toString());
    }

    @Issue("SECURITY-382")
    @Test
    public void oldDeserialization() throws Exception {
        ByteBuffer buf = new ByteBuffer();
        buf.write(("hello"
                        + ConsoleNote.PREAMBLE_STR
                        + "AAAAwR+LCAAAAAAAAP9dzLEOwVAUxvHThtiNprYxsGiMQhiwNSIhMR/tSZXr"
                        + "3Lr3oJPwPt7FM5hM3gFh8i3/5Bt+1yeUrYH6ap9Yza1Ys9WKWuMiR05wqWhE"
                        + "gpmyEy306Jxvwb19ccGNoBJjLplmgWq0xgOGCjkNZ2IyTrsRlFayVTs4gVMY"
                        + "qP3pw28/JnznuABF/rYWyIyeJfLQe1vxZiDQ7NnYZLn0UZGRRjA9MiV+0OyF"
                        + "v3+utadQyH8B+aJxVM4AAAA="
                        + ConsoleNote.POSTAMBLE_STR
                        + "there\n")
                .getBytes(StandardCharsets.UTF_8));
        AnnotatedLargeText<Void> text = new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        text.writeLogTo(0, baos);
        assertEquals("hellothere\n", baos.toString(StandardCharsets.UTF_8));
        StringWriter w = new StringWriter();
        text.writeHtmlTo(0, w);
        assertEquals("hellothere\n", w.toString());
        assertThat(logging.getMessages(), hasItem(
                "Failed to resurrect annotation from \"\\u001B[8mha:AAAAwR+LC"
                        + "AAAAAAAAP9dzLEOwVAUxvHThtiNprYxsGiMQhiwNSIhMR/tSZXr3Lr3oJPwP"
                        + "t7FM5hM3gFh8i3/5Bt+1yeUrYH6ap9Yza1Ys9WKWuMiR05wqWhEgpmyEy306"
                        + "Jxvwb19ccGNoBJjLplmgWq0xgOGCjkNZ2IyTrsRlFayVTs4gVMYqP3pw28/J"
                        + "nznuABF/rYWyIyeJfLQe1vxZiDQ7NnYZLn0UZGRRjA9MiV+0OyFv3+utadQy"
                        + "H8B+aJxVM4AAAA=\\u001B[0mthere\\n\"")); // TODO assert that this is IOException: Refusing to deserialize unsigned note from an old log.
        ConsoleNote.INSECURE = true;
        try {
            w = new StringWriter();
            text.writeHtmlTo(0, w);
            assertThat(w.toString(), containsString("<script>"));
        } finally {
            ConsoleNote.INSECURE = false;
        }
    }

    @Issue("SECURITY-382")
    @Test
    public void badMac() throws Exception {
        ByteBuffer buf = new ByteBuffer();
        buf.write(("Go back to "
                        + ConsoleNote.PREAMBLE_STR
                        + "////4ByIhqPpAc43AbrEtyDUDc1/UEOXsoY6LeoHSeSlb1d7AAAAlR+LCAAA"
                        + "AAAAAP9b85aBtbiIQS+jNKU4P08vOT+vOD8nVc8xLy+/JLEkNcUnsSg9NSS1"
                        + "oiQktbhEBUT45ZekCpys9xWo8J3KxMDkycCWk5qXXpLhw8BcWpRTwiDkk5VY"
                        + "lqifk5iXrh9cUpSZl25dUcQghWaBM4QGGcYAAYxMDAwVBUAGZwkDq35Rfn4J"
                        + "ABmN28qcAAAA"
                        + ConsoleNote.POSTAMBLE_STR
                        + "your home.\n")
                .getBytes(StandardCharsets.UTF_8));
        AnnotatedLargeText<Void> text = new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        text.writeLogTo(0, baos);
        assertEquals("Go back to your home.\n", baos.toString(StandardCharsets.UTF_8));
        StringWriter w = new StringWriter();
        text.writeHtmlTo(0, w);
        assertEquals("Go back to your home.\n", w.toString());
        assertThat(logging.getMessages(), hasItem(
                "Failed to resurrect annotation from \"\\u001B[8mha:////4ByIh"
                        + "qPpAc43AbrEtyDUDc1/UEOXsoY6LeoHSeSlb1d7AAAAlR+LCAAAAAAAAP9b8"
                        + "5aBtbiIQS+jNKU4P08vOT+vOD8nVc8xLy+/JLEkNcUnsSg9NSS1oiQktbhEB"
                        + "UT45ZekCpys9xWo8J3KxMDkycCWk5qXXpLhw8BcWpRTwiDkk5VYlqifk5iXr"
                        + "h9cUpSZl25dUcQghWaBM4QGGcYAAYxMDAwVBUAGZwkDq35Rfn4JABmN28qcA"
                        + "AAA\\u001B[0myour home.\\n\"")); // TODO assert that this is IOException: MAC mismatch
    }

    @Issue("JENKINS-61452")
    @Test
    public void corruptedNote() throws Exception {
        ByteBuffer buf = new ByteBuffer();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        ps.print("Some text.\n");
        ps.print("Go back to " + TestNote.encodeTo("/root", "your home") + ".\n");
        ps.print("More text.\n");
        String original = buf.toString();
        String corrupted = original.replace("+", "\u0000");
        buf = new ByteBuffer();
        buf.write(corrupted.getBytes());
        AnnotatedLargeText<Void> text = new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, true, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        text.writeLogTo(0, baos);
        assertThat(baos.toString(StandardCharsets.UTF_8), matchesRegex("Some text[.]\nGo back to .*your home[.]\nMore text[.]\n"));
        assertThat(logging.getMessages(), hasItem(matchesRegex("Failed to skip annotation from .+")));
        StringWriter w = new StringWriter();
        text.writeHtmlTo(0, w);
        assertThat(w.toString(), matchesRegex("Some text[.]\nGo back to .*your home[.]\nMore text[.]\n"));
        assertThat(logging.getMessages(), hasItem(matchesRegex("Failed to resurrect annotation from .+")));
    }

    /** Simplified version of {@link HyperlinkNote}. */
    static class TestNote extends ConsoleNote<Void> {
        private final String url;
        private final int length;

        TestNote(String url, int length) {
            this.url = url;
            this.length = length;
        }

        @Override
        public ConsoleAnnotator<?> annotate(Void context, MarkupText text, int charPos) {
            text.addMarkup(charPos, charPos + length, "<a href='" + url + "'" + ">", "</a>");
            return null;
        }

        static String encodeTo(String url, String text) throws IOException {
            return new TestNote(url, text.length()).encode() + text;
        }
    }

}
