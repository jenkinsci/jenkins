/*
 * The MIT License
 *
 * Copyright (c) 2018, Tenable, Inc.
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
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns a text into a anchor link by specifying the anchor separately.
 * Allows to create links directly into a longer log.
 *
 * @author Ralph Boerger based on HyperlinkNote by Kohsuke Kawaguchi
 * @since TODO
 */
public class AnchorNote extends ConsoleNote {
    private final String anchor;
    private final int length;

    public AnchorNote(String anchor, int length) {
        this.anchor = anchor;
        this.length = length;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        if (length == 0) {
            text.addMarkup(charPos, charPos + length, "<a name='" + anchor + "'"+extraAttributes()+"></a>", "");
        }
        else {
            text.addMarkup(charPos, charPos + length, "<a name='" + anchor + "'"+extraAttributes()+">", "</a>");
        }
        return null;
    }

    protected String extraAttributes() {
        return "";
    }

    public static String encodeTo(String anchor, String text) {
        return encodeTo(anchor, text, AnchorNote::new);
    }

    @Restricted(NoExternalUse.class)
    static String encodeTo(String anchor, String text, BiFunction<String, Integer, ConsoleNote> constructor) {
        // If text contains newlines, then its stored length will not match its length when being
        // displayed, since the display length will only include text up to the first newline,
        // which will cause an IndexOutOfBoundsException in MarkupText#rangeCheck when
        // ConsoleAnnotationOutputStream converts the note into markup. That stream treats '\n' as
        // the sole end-of-line marker on all platforms, so we ignore '\r' because it will not
        // break the conversion.
        text = text.replace('\n', ' ');

        try {
            return constructor.apply(anchor,text.length()).encode()+text;
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize "+AnchorNote.class,e);
            return text;
        }
    }

    @Extension @Symbol("anchor")
    public static class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Anchor";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AnchorNote.class.getName());
}
