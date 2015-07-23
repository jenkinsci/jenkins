/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package com.gargoylesoftware.htmlunit.html;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.Page;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HtmlFormUtil {

    /**
     * Plain {@link com.gargoylesoftware.htmlunit.html.HtmlForm#submit()} doesn't work correctly due to the use of YUI in Hudson.
     */
    public static Page submit(final HtmlForm htmlForm) throws IOException {
        final HtmlSubmitInput submitElement = getSubmitButton(htmlForm);
        return submit(htmlForm, submitElement);
    }

    /**
     * Plain {@link com.gargoylesoftware.htmlunit.html.HtmlForm#submit()} doesn't work correctly due to the use of YUI in Hudson.
     */
    public static Page submit(HtmlForm htmlForm, HtmlSubmitInput submitElement) throws IOException {
        if (submitElement != null) {
            // To make YUI event handling work, this combo seems to be necessary
            // the click will trigger _onClick in buton-*.js, but it doesn't submit the form
            // (a comment alluding to this behavior can be seen in submitForm method)
            // so to complete it, submit the form later.
            //
            // Just doing form.submit() doesn't work either, because it doesn't do
            // the preparation work needed to pass along the name of the button that
            // triggered a submission (more concretely, m_oSubmitTrigger is not set.)
            submitElement.click();
        }
        return htmlForm.submit(submitElement);
    }

    /**
     * Returns all the &lt;input type="submit"> elements in this form.
     */
    public static List<HtmlSubmitInput> getSubmitButtons(final HtmlForm htmlForm) throws ElementNotFoundException {
        final List<HtmlSubmitInput> list = htmlForm.getElementsByAttribute("input", "type", "submit");

        // collect inputs from lost children
        for (final HtmlElement elt : htmlForm.getLostChildren()) {
            if (elt instanceof HtmlSubmitInput) {
                list.add((HtmlSubmitInput) elt);
            }
        }
        return list;
    }

    /**
     * Gets the first &lt;input type="submit"> element in this form.
     */
    public static HtmlSubmitInput getSubmitButton(final HtmlForm htmlForm) throws ElementNotFoundException {
        return getSubmitButtons(htmlForm).get(0);
    }

    public static HtmlSubmitInput getButtonByCaption(final HtmlForm htmlForm, final String caption) throws ElementNotFoundException {
        for (HtmlElement b : htmlForm.getHtmlElementsByTagName("button")) {
            if(b.getTextContent().trim().equals(caption))
                return (HtmlSubmitInput) b;
        }
        throw new ElementNotFoundException("button", "caption", caption);
    }
}
