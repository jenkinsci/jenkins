/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, CloudBees, Inc.
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

import hudson.Extension;
import hudson.MarkupText;
import org.jenkinsci.Symbol;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placed on the beginning of the exception stack trace produced by Hudson, which in turn produces hyperlinked stack trace.
 *
 * <p>
 * Exceptions in the user code (like junit etc) should be handled differently. This is only for exceptions
 * that occur inside Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public class HudsonExceptionNote extends ConsoleNote<Object> {

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        // An exception stack trace looks like this:
        // org.acme.FooBarException: message
        // <TAB>at org.acme.Foo.method(Foo.java:123)
        // Caused by: java.lang.ClassNotFoundException:
        String line = text.getText();
        int end = line.indexOf(':',charPos);
        if (end<0) {
            if (CLASSNAME.matcher(line.substring(charPos)).matches())
                end = line.length();
            else
                return null;    // unexpected format. abort.
        }
        text.addHyperlinkLowKey(charPos,end,annotateClassName(line.substring(charPos,end)));

        return new ConsoleAnnotator() {
            public ConsoleAnnotator annotate(Object context, MarkupText text) {
                String line = text.getText();

                Matcher m = STACK_TRACE_ELEMENT.matcher(line);
                if (m.find()) {// allow the match to happen in the middle of a line to cope with prefix. Ant and Maven put them, among many other tools.
                    text.addHyperlinkLowKey(m.start()+4,m.end(),annotateMethodName(m.group(1),m.group(2),m.group(3),Integer.parseInt(m.group(4))));
                    return this;
                }

                int idx = line.indexOf(CAUSED_BY);
                if (idx>=0) {
                    int s = idx + CAUSED_BY.length();
                    int e = line.indexOf(':', s);
                    if (e<0)    e = line.length();
                    text.addHyperlinkLowKey(s,e,annotateClassName(line.substring(s,e)));
                    return this;
                }

                if (AND_MORE.matcher(line).matches())
                    return this;

                // looks like we are done with the stack trace
                return null;
            }
        };
    }

    // TODO; separate out the annotations and mark up

    private String annotateMethodName(String className, String methodName, String sourceFileName, int lineNumber) {
        return "http://stacktrace.jenkins-ci.org/search/?query="+className+'.'+methodName+"&entity=method";
    }

    private String annotateClassName(String className) {
        return "http://stacktrace.jenkins-ci.org/search?query="+className;
    }

    @Extension @Symbol("stackTrace")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @Override
        public String getDisplayName() {
            return "Exception Stack Trace";
        }
    }

    /**
     * Regular expression that represents a valid class name.
     */
    private static final String CLASSNAME_PATTERN = "[\\p{L}0-9$_.]+";

    private static final Pattern CLASSNAME = Pattern.compile(CLASSNAME_PATTERN+"\r?\n?");

    /**
     * Matches to the line like "\tat org.acme.Foo.method(File.java:123)"
     * and captures class name, method name, source file name, and line number separately.
     */
    private static final Pattern STACK_TRACE_ELEMENT = Pattern.compile("\tat ("+CLASSNAME_PATTERN+")\\.([\\p{L}0-9$_<>]+)\\((\\S+):([0-9]+)\\)");

    private static final String CAUSED_BY = "Caused by: ";

    private static final Pattern AND_MORE = Pattern.compile("\t... [0-9]+ more\n");
}
