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

/**
 * Content Security Policy (CSP) implementation for the Jenkins UI.
 * <p>
 *     {@link jenkins.security.csp.Filter} and {@link jenkins.security.csp.Decorator}
 *     set {@code Content-Security-Policy} and {@code Reporting-Endpoints} headers
 *     with the value built by {@link jenkins.security.csp.CspBuilder}
 *     (plus {@code report-to} and {@code report-uri}).
 * </p>
 * <p>
 *     Plugins can contribute CSP directives via {@link jenkins.security.csp.Contributor}
 *     ({@link jenkins.security.csp.SimpleContributor} for basic use cases) and receive
 *     violation reports via {@link jenkins.security.csp.CspReceiver}.
 * </p>
 * <p>
 *     {@link jenkins.security.csp.Contributor} configures directives through
 *     {@link jenkins.security.csp.CspBuilder}'s methods. {@link jenkins.security.csp.Directive}
 *     and {@link jenkins.security.csp.FetchDirective} model available directives and values.
 * </p>
 * <p>
 * Related Java system properties:
 * <ul>
 *   <li>{@code jenkins.security.csp.Decorator.headerName} - Override CSP header name</li>
 *   <li>{@code hudson.model.DirectoryBrowserSupport.CSP} - Override user content (e.g. workspace) CSP</li>
 * </ul>
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP">MDN: Content Security Policy</a>
 * @see jenkins.security.csp.Contributor
 * @see jenkins.security.csp.CspReceiver
 */
@org.kohsuke.accmod.Restricted(org.kohsuke.accmod.restrictions.Beta.class)
package jenkins.security.csp;
