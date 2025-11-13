/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp;

import java.util.Optional;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * The fetch directives and their inheritance rules (in {@link #getFallback()}).
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Glossary/Fetch_directive">MDN</a>
 * @since TODO
 */
@Restricted(Beta.class)
public enum FetchDirective {
    DEFAULT_SRC(Directive.DEFAULT_SRC),
    CHILD_SRC(Directive.CHILD_SRC),
    CONNECT_SRC(Directive.CONNECT_SRC),
    FONT_SRC(Directive.FONT_SRC),
    FRAME_SRC(Directive.FRAME_SRC),
    IMG_SRC(Directive.IMG_SRC),
    MANIFEST_SRC(Directive.MANIFEST_SRC),
    MEDIA_SRC(Directive.MEDIA_SRC),
    OBJECT_SRC(Directive.OBJECT_SRC),
    PREFETCH_SRC(Directive.PREFETCH_SRC),
    SCRIPT_SRC(Directive.SCRIPT_SRC),
    SCRIPT_SRC_ELEM(Directive.SCRIPT_SRC_ELEM),
    SCRIPT_SRC_ATTR(Directive.SCRIPT_SRC_ATTR),
    STYLE_SRC(Directive.STYLE_SRC),
    STYLE_SRC_ELEM(Directive.STYLE_SRC_ELEM),
    STYLE_SRC_ATTR(Directive.STYLE_SRC_ATTR),
    WORKER_SRC(Directive.WORKER_SRC);

    private final String key;

    FetchDirective(String s) {
        this.key = s;
    }

    public String toKey() {
        return key;
    }

    /**
     * Returns the {@link jenkins.security.csp.FetchDirective} corresponding to
     * the specified key. For example, the parameter {@code default-src} will
     * return {@link #DEFAULT_SRC}.
     *
     * @param s the key for the directive
     * @return the {@link jenkins.security.csp.FetchDirective} corresponding to the key
     */
    public static FetchDirective fromKey(String s) {
        for (FetchDirective e : FetchDirective.values()) {
            if (e.key.equals(s)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Key not found: " + s);
    }

    /**
     * Returns true if and only if the specified key is a {@link jenkins.security.csp.FetchDirective}.
     * Returns {@code true} for {@code script-src}, {@code false} for {@code sandbox}.
     */
    public static boolean isFetchDirective(String key) {
        return toFetchDirective(key).isPresent();
    }

    /**
     * Similar to {@link #fromKey(String)}, this returns the corresponding
     * {@link jenkins.security.csp.FetchDirective} wrapped in {@link java.util.Optional}.
     * If the specified key does not correspond to a fetch directive, instead leaves the Optional empty.
     *
     * @param key the key for the directive
     * @return an {@link java.util.Optional} containing the corresponding {@link jenkins.security.csp.FetchDirective}, or left empty if there is none.
     */
    public static Optional<FetchDirective> toFetchDirective(String key) {
        try {
            return Optional.of(fromKey(key));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Which element is used as fallback if one is undefined.
     * For {@code *-src-elem} and {@code *-src-attr} this is the corresponding
     * {@code *-src}, for {@code frame-src} and {@code worker-src} this is
     * {@code child-src}, for everything else, except {@code default-src}, it's
     * {@code default-src}.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy#fallbacks">MDN</a>
     *
     * @return The fallback directive if this one is unspecified, or {@code null}
     * if there is no fallback.
     */
    public FetchDirective getFallback() {
        if (this == SCRIPT_SRC_ATTR || this == SCRIPT_SRC_ELEM) {
            return SCRIPT_SRC;
        }
        if (this == STYLE_SRC_ATTR || this == STYLE_SRC_ELEM) {
            return STYLE_SRC;
        }
        if (this == FRAME_SRC || this == WORKER_SRC) {
            return CHILD_SRC;
        }
        if (this != DEFAULT_SRC) {
            return DEFAULT_SRC;
        }
        return null;
    }
}
