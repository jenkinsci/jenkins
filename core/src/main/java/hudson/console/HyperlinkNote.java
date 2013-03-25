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
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns a text into a hyperlink by specifying the URL separately.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.362
 * @see ModelHyperlinkNote
 */
public class HyperlinkNote extends ConsoleNote {
    /**
     * If this starts with '/', it's interpreted as a path within the context path.
     */
    private final String url;
    private final int length;

    public HyperlinkNote(String url, int length) {
        this.url = url;
        this.length = length;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        String url = this.url;
        if (url.startsWith("/")) {
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req!=null) {
                // if we are serving HTTP request, we want to use app relative URL
                url = req.getContextPath()+url;
            } else {
                // otherwise presumably this is rendered for e-mails and other non-HTTP stuff
                url = Jenkins.getInstance().getRootUrl()+url.substring(1);
            }
        }
        text.addMarkup(charPos, charPos + length, "<a href='" + url + "'"+extraAttributes()+">", "</a>");
        return null;
    }

    protected String extraAttributes() {
        return "";
    }

    public static String encodeTo(String url, String text) {
        try {
            return new HyperlinkNote(url,text.length()).encode()+text;
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize "+HyperlinkNote.class,e);
            return text;
        }
    }

    @Extension
    public static class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Hyperlinks";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(HyperlinkNote.class.getName());
}
