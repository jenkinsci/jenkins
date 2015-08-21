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
import com.gargoylesoftware.htmlunit.WebClient;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HtmlFormUtil {

    public static Page submit(final HtmlForm htmlForm) throws IOException {
        HtmlElement submitElement = getSubmitButton(htmlForm);
        return submit(htmlForm, submitElement);
    }

    public static Page submit(HtmlForm htmlForm, HtmlElement submitElement) throws IOException {
        if (submitElement != null && !(submitElement instanceof SubmittableElement)) {
            // Just click and return
            return submitElement.click();
        }

        final HtmlPage htmlPage = (HtmlPage) htmlForm.getPage();
        final WebClient webClient = htmlPage.getWebClient();

        try {
            htmlForm.submit((SubmittableElement) submitElement);
        } finally {
            // The HtmlForm submit doesn't really do anything. It just adds a "LoadJob"
            // to an internal queue. What we are doing here is manually forcing the load of
            // the response for that submit LoadJob and then getting the enclosing page
            // from the current window on the WebClient, allowing us to return the correct
            // HtmlPage object to the test.
            webClient.loadDownloadedResponses();
            Page resultPage = webClient.getCurrentWindow().getEnclosedPage();

            if (resultPage == htmlPage) {
                // We're still on the same page (form submit didn't bring us anywhere).
                // Hackery. Seems like YUI is messing us about.
                return submitElement.click();
            }

            return resultPage;
        }
    }

    /**
     * Returns all the &lt;input type="submit"> elements in this form.
     */
    public static List<HtmlElement> getSubmitButtons(final HtmlForm htmlForm) throws ElementNotFoundException {
        final List<HtmlElement> list = htmlForm.getElementsByAttribute("input", "type", "submit");

        // collect inputs from lost children
        for (final HtmlElement elt : htmlForm.getLostChildren()) {
            list.add(elt);
        }

        return list;
    }

    /**
     * Gets the first &lt;input type="submit"> element in this form.
     */
    public static HtmlElement getSubmitButton(final HtmlForm htmlForm) throws ElementNotFoundException {
        List<HtmlElement> submitButtons = getSubmitButtons(htmlForm);
        if (!submitButtons.isEmpty()) {
            return submitButtons.get(0);
        }
        for (HtmlElement element : htmlForm.getHtmlElementsByTagName("button")) {
            if(element instanceof HtmlButton) {
                return element;
            }
        }
        return null;
    }

    public static HtmlButton getButtonByCaption(final HtmlForm htmlForm, final String caption) throws ElementNotFoundException {
        for (HtmlElement b : htmlForm.getHtmlElementsByTagName("button")) {
            if(b instanceof HtmlButton && b.getTextContent().trim().equals(caption)) {
                return (HtmlButton) b;
            }
        }
        throw new ElementNotFoundException("button", "caption", caption);
    }
}
