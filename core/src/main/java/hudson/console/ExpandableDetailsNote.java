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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders a button that can be clicked to reveal additional block tag (and HTML inside it.)
 *
 * <p>
 * Useful if you want the user to be able to see additional details.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.395
 */
public class ExpandableDetailsNote extends ConsoleNote {
    private final String caption;
    private final String html;

    public ExpandableDetailsNote(String caption, String html) {
        this.caption = caption;
        this.html = html;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(charPos,
                "<button type='button' class='jenkins-button reveal-expandable-detail'>"
                        + Util.xmlEscape(caption) + "</button><div class='expandable-detail'>" + html + "</div>");
        return null;
    }

    public static String encodeTo(String buttonCaption, String html) {
        try {
            return new ExpandableDetailsNote(buttonCaption, html).encode();
        } catch (IOException e) {
            // impossible, but don't make this a fatal problem
            LOGGER.log(Level.WARNING, "Failed to serialize " + HyperlinkNote.class, e);
            return "";
        }
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Expandable details";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExpandableDetailsNote.class.getName());
}
