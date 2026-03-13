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

import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Represents a defined Content Security Policy directive
 * @param name {@code default-src}, {@code frame-ancestors}, etc.
 * @param inheriting whether the directive is inheriting or not. Only applies to
 *  directives based on {@link jenkins.security.csp.FetchDirective}.
 * @param values {@code 'self'}, {@code data:}, {@code jenkins.io}, etc.
 *
 * @since 2.539
 */
@Restricted(Beta.class)
public record Directive(String name, Boolean inheriting, List<String> values) {

    /* Fetch directives */
    public static final String DEFAULT_SRC = "default-src";
    public static final String CHILD_SRC = "child-src";
    public static final String CONNECT_SRC = "connect-src";
    public static final String FONT_SRC = "font-src";
    public static final String FRAME_SRC = "frame-src";
    public static final String IMG_SRC = "img-src";
    public static final String MANIFEST_SRC = "manifest-src";
    public static final String MEDIA_SRC = "media-src";
    public static final String OBJECT_SRC = "object-src";
    public static final String PREFETCH_SRC = "prefetch-src";
    public static final String SCRIPT_SRC = "script-src";
    public static final String SCRIPT_SRC_ELEM = "script-src-elem";
    public static final String SCRIPT_SRC_ATTR = "script-src-attr";
    public static final String STYLE_SRC = "style-src";
    public static final String STYLE_SRC_ELEM = "style-src-elem";
    public static final String STYLE_SRC_ATTR = "style-src-attr";
    public static final String WORKER_SRC = "worker-src";
    /* Fetch directives end */


    /* Other directives */
    public static final String BASE_URI = "base-uri";

    /**
     * Deprecated directive.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/block-all-mixed-content">MDN</a>
     * @deprecated by CSP spec
     */
    @Deprecated
    public static final String BLOCK_ALL_MIXED_CONTENT = "block-all-mixed-content";
    public static final String FORM_ACTION = "form-action";
    public static final String FRAME_ANCESTORS = "frame-ancestors";

    /**
     * Unsupported for use in plugins.
     *
     * @see CspBuilder#PROHIBITED_KEYS
     */
    @Restricted(NoExternalUse.class)
    public static final String REPORT_TO = "report-to";

    /**
     * Deprecated directive. Intended to be replaced by {@link #REPORT_TO}. Unsupported for use in plugins.
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/report-uri">MDN</a>
     * @see CspBuilder#PROHIBITED_KEYS
     * @deprecated by CSP spec
     */
    @Restricted(NoExternalUse.class)
    @Deprecated
    public static final String REPORT_URI = "report-uri";
    public static final String REQUIRE_TRUSTED_TYPES_FOR = "require-trusted-types-for";
    public static final String SANDBOX = "sandbox";
    public static final String TRUSTED_TYPES = "trusted-types";
    public static final String UPGRADE_INSECURE_REQUESTS = "upgrade-insecure-requests";
    /* Other directives end */


    /* Values */
    public static final String SELF = "'self'";
    /**
     * Disallow all.
     * Note that this is not a valid argument for {@link CspBuilder#add(String, String...)}.
     * To initialize a previously undefined fetch directive, call {@link CspBuilder#initialize(FetchDirective, String...)} and pass no values.
     * To remove all other values, call {@link CspBuilder#remove(String, String...)}.
     */
    public static final String NONE = "'none'";
    public static final String UNSAFE_INLINE = "'unsafe-inline'";

    /**
     * Probably should not be used.
     *
     * @deprecated This should not be used.
     */
    @Deprecated // Indicator for discouraged use.
    public static final String UNSAFE_EVAL = "'unsafe-eval'";
    public static final String DATA = "data:";
    public static final String BLOB = "blob:";
    public static final String REPORT_SAMPLE = "'report-sample'";
    /* Values end */
}
