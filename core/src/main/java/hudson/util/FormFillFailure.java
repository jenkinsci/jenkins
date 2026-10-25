/*
 * The MIT License
 *
 * Copyright (c) 2004-2017, Sun Microsystems, Inc., CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.Util;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Represents a failure in a form field doFillXYZ method.
 *
 * <p>
 * Use one of the factory methods to create an instance, then throw it from your {@code doFillXyz}
 * method.
 *
 * @since 2.50
 */
public abstract class FormFillFailure extends IOException implements HttpResponse {

    /**
     * Sends out a string error message that indicates an error.
     *
     * @param message Human readable message to be sent.
     */
    public static FormFillFailure error(@NonNull String message) {
        return errorWithMarkup(Util.escape(message));
    }

    public static FormFillFailure warning(@NonNull String message) {
        return warningWithMarkup(Util.escape(message));
    }

    /**
     * Sends out a string error message that indicates an error,
     * by formatting it with {@link String#format(String, Object[])}
     */
    public static FormFillFailure error(String format, Object... args) {
        return error(String.format(format, args));
    }

    public static FormFillFailure warning(String format, Object... args) {
        return warning(String.format(format, args));
    }

    /**
     * Sends out a string error message, with optional "show details" link that expands to the full stack trace.
     *
     * <p>
     * Use this with caution, so that anonymous users do not gain too much insights into the state of the system,
     * as error stack trace often reveals a lot of information. Consider if an error needs to be exposed
     * to everyone or just those who have higher access to job/hudson/etc.
     */
    public static FormFillFailure error(Throwable e, String message) {
        return _error(FormValidation.Kind.ERROR, e, message);
    }

    public static FormFillFailure warning(Throwable e, String message) {
        return _error(FormValidation.Kind.WARNING, e, message);
    }

    private static FormFillFailure _error(FormValidation.Kind kind, Throwable e, String message) {
        if (e == null) {
            return _errorWithMarkup(Util.escape(message), kind);
        }

        return _errorWithMarkup(Util.escape(message) +
                " </div><div><a href='#' class='showDetails'>"
                + Messages.FormValidation_Error_Details()
                + "</a><pre style='display:none'>"
                + Util.escape(Functions.printThrowable(e)) +
                "</pre>", kind
        );
    }

    public static FormFillFailure error(Throwable e, String format, Object... args) {
        return error(e, String.format(format, args));
    }

    public static FormFillFailure warning(Throwable e, String format, Object... args) {
        return warning(e, String.format(format, args));
    }

    /**
     * Sends out an HTML fragment that indicates an error.
     *
     * <p>
     * This method must be used with care to avoid cross-site scripting
     * attack.
     *
     * @param message Human readable message to be sent. {@code error(null)}
     *                can be used as {@code ok()}.
     */
    public static FormFillFailure errorWithMarkup(String message) {
        return _errorWithMarkup(message, FormValidation.Kind.ERROR);
    }

    public static FormFillFailure warningWithMarkup(String message) {
        return _errorWithMarkup(message, FormValidation.Kind.WARNING);
    }

    private static FormFillFailure _errorWithMarkup(@NonNull final String message, final FormValidation.Kind kind) {
        return new FormFillFailure(kind, message) {
            @Override
            public String renderHtml() {
                StaplerRequest2 req = Stapler.getCurrentRequest2();
                if (req == null) { // being called from some other context
                    return message;
                }
                return "<div class=" + getKind().name().toLowerCase(Locale.ENGLISH) + ">" +
                        message + "</div>";
            }

            @Override
            public String toString() {
                return kind + ": " + message;
            }
        };
    }

    /**
     * Sends out an arbitrary HTML fragment as the output.
     */
    public static FormFillFailure respond(FormValidation.Kind kind, final String html) {
        return new FormFillFailure(kind) {
            @Override
            public String renderHtml() {
                return html;
            }

            @Override
            public String toString() {
                return getKind() + ": " + html;
            }
        };
    }

    private final FormValidation.Kind kind;
    private boolean selectionCleared;

    /**
     * Instances should be created via one of the factory methods above.
     *
     * @param kind the kind
     */
    private FormFillFailure(FormValidation.Kind kind) {
        this.kind = kind;
    }

    private FormFillFailure(FormValidation.Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node)
            throws IOException, ServletException {
        rsp.setContentType("text/html;charset=UTF-8");
        rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        rsp.setHeader("X-Jenkins-Select-Error", selectionCleared ? "clear" : "retain");
        rsp.getWriter().print(renderHtml());
    }

    public FormValidation.Kind getKind() {
        return kind;
    }

    public boolean isSelectionCleared() {
        return selectionCleared;
    }

    /**
     * Flags this failure as requiring that the select options should be cleared out.
     *
     * @return {@code this} for method chaining.
     */
    public FormFillFailure withSelectionCleared() {
        this.selectionCleared = true;
        return this;
    }

    public abstract String renderHtml();

}
