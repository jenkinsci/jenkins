/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.tasks._maven;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import org.jenkinsci.Symbol;

import java.util.regex.Pattern;

/**
 * Marks the log line that reports that Maven3 is executing a mojo.
 * It'll look something like this:
 *
 * <pre>[INFO] --- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---</pre>
 *
 * or
 *
 * <pre>[INFO] --- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---</pre>
 *
 * or
 *
 * <pre>[INFO] --- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---</pre>
 *
 * @author Mirko Friedenhagen
 */
public class Maven3MojoNote extends ConsoleNote {
    public Maven3MojoNote() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(7,text.length(),"<b class=maven-mojo>","</b>");
        return null;
    }

    @Extension @Symbol("maven3Mojos")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Maven 3 Mojos";
        }
    }

    public static Pattern PATTERN = Pattern.compile("\\[INFO\\] --- .+-plugin:[^:]+:[^ ]+ \\(.+\\) @ .+ ---");
}
