/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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
package hudson.util;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class AlternativeUiTextProviderTest extends HudsonTestCase {

    @TestExtension
    public static class Impl extends AlternativeUiTextProvider {
        @Override public <T> String getText(Message<T> text, T context) {
            if (text==AbstractProject.BUILD_NOW_TEXT)
                return "XYZ:"+AbstractProject.BUILD_NOW_TEXT.cast(context).getDisplayName();
            return null;
        }
    }

    /**
     * Makes sure that {@link AlternativeUiTextProvider} actually works at some basic level.
     */
    public void testBasics() throws Exception {
        FreeStyleProject p = createFreeStyleProject("aaa");
        assertTrue(createWebClient().getPage(p).asText().contains("XYZ:aaa"));
    }
}

