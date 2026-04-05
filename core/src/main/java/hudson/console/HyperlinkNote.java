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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.MarkupText;
import hudson.Util;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

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
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null) {
                // if we are serving HTTP request, we want to use app relative URL
                url = req.getContextPath() + url;
            } else {
                // otherwise presumably this is rendered for e-mails and other non-HTTP stuff
                url = Jenkins.get().getRootUrl() + url.substring(1);
            }
        }
        text.addMarkup(charPos, charPos + length, "<a href='" + Util.escape(url) + "'" + extraAttributes() + ">", "</a>");
        return null;
    }

    protected String extraAttributes() {
        return "";
    }

    public static String encodeTo(String url, String text) {
        return encodeTo(url, text, HyperlinkNote::new);
    }

    @Restricted(NoExternalUse.class)
    static String encodeTo(String url, String text, BiFunction<String, Integer, ConsoleNote> constructor) {
        // If text contains newlines, then its stored length will not match its length when being
        // displayed, since the display length will only include text up to the first newline,
        // which will cause an IndexOutOfBoundsException in MarkupText#rangeCheck when
        // ConsoleAnnotationOutputStream converts the note into markup.
        text = text.replace('\n', ' ').replace('\r', ' ');
        try {
            return constructor.apply(url, text.length()).encode() + text;
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize " + HyperlinkNote.class, e);
            return text;
        }
    }

    @Extension @Symbol("hyperlink")
    public static class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Hyperlinks";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(HyperlinkNote.class.getName());
    private static final long serialVersionUID = 3908468829358026949L;
}
