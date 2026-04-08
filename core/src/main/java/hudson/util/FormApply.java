/*
 * The MIT License
 *
 * Copyright (c) 2012-, CloudBees, Inc.
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

package hudson.util;

import hudson.Functions;
import jakarta.servlet.ServletException;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Server-side code related to the {@code <f:apply>} button.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.453
 */
public class FormApply {
    /**
     * Generates the response for the form submission in such a way that it handles the "apply" button
     * correctly.
     *
     * @param destination
     *      The page that the user will be taken to upon a successful submission (in case this is not via the "apply" button.)
     */
    public static HttpResponseException success(final String destination) {
        return new HttpResponseException() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                if (isApply(req)) {
                    // if the submission is via 'apply', show a response in the notification bar
                    showNotification(Messages.HttpResponses_Saved(), NotificationType.SUCCESS)
                            .generateResponse(req, rsp, node);
                } else {
                    rsp.sendRedirect(destination);
                }
            }
        };
    }

    /**
     * Is this submission from the "apply" button?
     *
     * @since 2.475
     */
    public static boolean isApply(StaplerRequest2 req) {
        return Boolean.parseBoolean(req.getParameter("core:apply"));
    }


    /**
     * @deprecated use {@link #isApply(StaplerRequest2)}
     */
    @Deprecated
    public static boolean isApply(StaplerRequest req) {
        return isApply(StaplerRequest.toStaplerRequest2(req));
    }

    /**
     * Generates the response for the asynchronous background form submission (AKA the Apply button.)
     * <p>
     * When the response HTML includes a JavaScript function in a pre-determined name, that function gets executed.
     * This method generates such a response from JavaScript text.
     *
     * @deprecated use {@link #showNotification(String, NotificationType)} instead, which is CSP compatible version
     */
    @Deprecated
    public static HttpResponseException applyResponse(final String script) {
        return new HttpResponseException() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                rsp.setContentType("text/html;charset=UTF-8");
                rsp.getWriter().println("<html><body><script>" +
                        "window.applyCompletionHandler = function (w) {" +
                        "  with(w) {" +
                        script +
                        "  }" +
                        "};" +
                        "</script></body></html>");
            }
        };
    }

    /**
     * Generates the response for the asynchronous background form submission (AKA the Apply button),
     * that will show a notification of certain type and with provided message.
     *
     * @param message a message to display in the popup. Only plain text is supported.
     * @param notificationType type of notification. See {@link NotificationType} for supported types. Defines the notification
     *                         color and the icon that will be shown.
     *
     * @since 2.482
     */
    public static HttpResponseException showNotification(final String message, final NotificationType notificationType) {
        return new HttpResponseException() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException {
                rsp.setContentType("text/html;charset=UTF-8");
                rsp.getWriter().println("<script id='form-apply-data-holder' data-message='" + Functions.htmlAttributeEscape(message) + "' " +
                        "data-notification-type='" + notificationType + "' " +
                        "src='" + req.getContextPath() + Jenkins.RESOURCE_PATH + "/scripts/apply.js" + "'></script>");
            }
        };
    }


    /**
     * Corresponds to types declared in <a href="https://github.com/jenkinsci/jenkins/blob/74610e024a6b8fd8feccdc51b8f7741aa6c30e3b/war/src/main/js/components/notifications/index.js#L13-L25">index.js</a>
     */
    public enum NotificationType {
        SUCCESS,
        WARNING,
        ERROR
    }
}
