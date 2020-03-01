/*
 * The MIT License
 *
 * Copyright (c) 2010-2017, CloudBees, Inc.
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

// TODO: the implementation has been deprecated due to JENKINS-42861
// Consider providing alternate search mechanisms (JIRA, grepcode, etc.) as proposed in 
// https://github.com/jenkinsci/jenkins/pull/2808#pullrequestreview-27467560 (JENKINS-43612)
/**
 * Placed on the beginning of the exception stack trace produced by Jenkins, 
 * which in turn produces hyperlinked stack trace.
 *
 * <p>
 * Exceptions in the user code (like junit etc) should be handled differently. This is only for exceptions
 * that occur inside Jenkins.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349 - produces search hyperlinks to the http://stacktrace.jenkins-ci.org service
 * @since 2.56 - does nothing due to JENKINS-42861
 * @deprecated This ConsoleNote used to provide hyperlinks to the
 *             {@code http://stacktrace.jenkins-ci.org/} service, which is dead now (JENKINS-42861).
 *             This console note does nothing right now.
 */
@Deprecated
public class HudsonExceptionNote extends ConsoleNote<Object> {

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {

        return null;
    }

    @Extension @Symbol("stackTrace")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @Override
        public String getDisplayName() {
            return "Exception Stack Trace";
        }
    }
}
