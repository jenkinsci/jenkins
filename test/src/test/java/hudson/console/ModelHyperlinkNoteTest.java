/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.FreeStyleProject;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class ModelHyperlinkNoteTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void textWithNewlines() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        String noteText = "\nthis string\nhas newline\r\ncharacters\n";
        String input = ModelHyperlinkNote.encodeTo(p, noteText);
        String noteTextSanitized = input.substring(input.length() - noteText.length());
        // Throws IndexOutOfBoundsException before https://github.com/jenkinsci/jenkins/pull/3580.
        String output = annotate(input);
        assertThat(output, allOf(
                containsString("href='" + r.getURL().toString()+p.getUrl() + "'"),
                containsString(">" + noteTextSanitized + "</a>")));
    }

    private String annotate(String text) throws IOException {
        StringWriter writer = new StringWriter();
        try (ConsoleAnnotationOutputStream out = new ConsoleAnnotationOutputStream(writer, null, null, StandardCharsets.UTF_8)) {
            IOUtils.copy(new StringReader(text), out);
        }
        return writer.toString();
    }
}
