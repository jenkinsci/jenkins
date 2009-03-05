/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.MissingAttributeException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jelly.expression.Expression;
import org.kohsuke.stapler.jelly.OutTag;
import org.springframework.beans.factory.annotation.Required;
import org.xml.sax.SAXException;

/**
 * Variant of {@linkplain OutTag <st:out>} that tries to hyperlink URLs.
 */
public class HyperlinkingOutTag extends TagSupport {

    private Expression value;

    @Required
    public void setValue(Expression value) {
        this.value = value;
    }

    public void doTag(XMLOutput output) throws MissingAttributeException, JellyTagException {
        String text = value.evaluateAsString(context);
        if (text != null) {
            try {
                output.write(text.
                        replace("&", "&amp;").
                        replace("<", "&lt;").
                        replaceAll("\\b(https?://[^\\s)>]+)", "<a href=\"$1\">$1</a>"));
            } catch (SAXException e) {
                throw new JellyTagException("could not write the XMLOutput", e);
            }
        }
    }
}
