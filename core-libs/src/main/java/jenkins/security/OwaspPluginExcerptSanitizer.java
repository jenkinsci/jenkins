/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.List;
import jenkins.core.PluginExcerptSanitizer;
import org.kohsuke.MetaInfServices;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.HtmlStreamEventProcessor;
import org.owasp.html.HtmlStreamEventReceiverWrapper;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * OWASP-based implementation of {@link jenkins.core.PluginExcerptSanitizer}
 */
@MetaInfServices(PluginExcerptSanitizer.class)
public class OwaspPluginExcerptSanitizer implements PluginExcerptSanitizer {

    /**
     * Add target=_blank to all links, so they open in a new window.
     */
    public static final HtmlStreamEventProcessor PRE_PROCESSOR = receiver -> new HtmlStreamEventReceiverWrapper(receiver) {
        @Override
        public void openTag(String elementName, List<String> attrs) {
            if ("a".equals(elementName)) {
                attrs.add("target");
                attrs.add("_blank");
            }
            super.openTag(elementName, attrs);
        }
    };

    /**
     * Policy that allows a safe subset of HTML suitable for plugin excerpts.
     * Allows basic formatting but strips scripts, iframes, and other dangerous elements.
     */
    private static final PolicyFactory EXCERPT_POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS)
            .and(new HtmlPolicyBuilder().withPreprocessor(PRE_PROCESSOR)
                    .allowElements("a")
                    .requireRelsOnLinks("noopener", "noreferrer")
                    .allowAttributes("target")
                    .matching(false, "_blank")
                    .onElements("a")
                    .toFactory());

    @Override
    @CheckForNull
    public String sanitize(@CheckForNull String html) {
        if (html == null) {
            return null;
        }
        return EXCERPT_POLICY.sanitize(html);
    }
}
