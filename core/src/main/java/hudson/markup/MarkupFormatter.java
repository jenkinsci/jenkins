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
package hudson.markup;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Hudson;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Generalization of a function that takes text with some markup and converts that to HTML.
 * Such markup is often associated with Wiki.
 *
 * <p>
 * Use of markup, as opposed to using raw HTML, ensures certain degree of security.
 *
 * <p>
 * This is an extension point in Hudson, allowing plugins to implement different markup formatters.
 *
 * <h2>Views</h2>
 * <p>
 * This extension point must have a valid <tt>config.jelly</tt> that feeds the constructor.
 *
 * TODO: allow {@link MarkupFormatter} to control the UI that the user uses to edit.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.391
 * @see Hudson#getMarkupFormatter()
 */
public abstract class MarkupFormatter extends AbstractDescribableImpl<MarkupFormatter> implements ExtensionPoint {
    /**
     * Given the text, converts that to HTML according to whatever markup rules implicit in the implementation class.
     *
     * <p>
     * Multiple threads can call this method concurrently with different inputs.
     *
     * @param output
     *      Formatted HTML should be sent to this output.
     */
    public abstract void translate(String markup, Writer output) throws IOException;

    public final String translate(String markup) throws IOException {
        StringWriter w = new StringWriter();
        translate(markup,w);
        return w.toString();
    }

    @Override
    public MarkupFormatterDescriptor getDescriptor() {
        return (MarkupFormatterDescriptor)super.getDescriptor();
    }
}
