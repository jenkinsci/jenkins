/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

import hudson.Util;
import hudson.scm.CVSSCM;
import hudson.model.Hudson;

/**
 * Represents the result of the form field validation.
 *
 * <p>
 * Use one of the factory methods to create an instance, then return it from your <tt>doCheckXyz</tt>
 * method. (Via {@link HttpResponse}, the returned object will render the result into {@link StaplerResponse}.)
 * This way of designing form field validation allows you to reuse {@code doCheckXyz()} methods
 * programmatically as well (by using {@link #kind}.
 *
 * <p>
 * See {@link CVSSCM.DescriptorImpl#doCheckCvsRoot(String)} as an example.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.294
 */
public abstract class FormValidation implements HttpResponse {
    /**
     * Indicates the kind of result.
     */
    public enum Kind {
        /**
         * Form field value was OK and no problem was detected.
         */
        OK,
        /**
         * Form field value contained something suspicious. For some limited use cases
         * the value could be valid, but we suspect the user made a mistake.
         */
        WARNING,
        /**
         * Form field value contained a problem that should be corrected.
         */
        ERROR
    }

    public static FormValidation ok() {
        return OK;
    }

    /**
     * Sends out a string error message that indicates an error.
     *
     * @param message
     *      Human readable message to be sent. <tt>error(null)</tt>
     *      can be used as <tt>ok()</tt>.
     */
    public static FormValidation error(String message) {
        return errorWithMarkup(message==null?null: Util.escape(message));
    }

    public static FormValidation warning(String message) {
        return warningWithMarkup(message==null?null:Util.escape(message));
    }

    public static FormValidation ok(String message) {
        return okWithMarkup(message==null?null:Util.escape(message));
    }

    /**
     * Sends out a string error message that indicates an error,
     * by formatting it with {@link String#format(String, Object[])}
     */
    public static FormValidation error(String format, Object... args) {
        return error(String.format(format,args));
    }

    public static FormValidation warning(String format, Object... args) {
        return warning(String.format(format,args));
    }

    public static FormValidation ok(String format, Object... args) {
        return ok(String.format(format,args));
    }

    /**
     * Sends out an HTML fragment that indicates an error.
     *
     * <p>
     * This method must be used with care to avoid cross-site scripting
     * attack.
     *
     * @param message
     *      Human readable message to be sent. <tt>error(null)</tt>
     *      can be used as <tt>ok()</tt>.
     */
    public static FormValidation errorWithMarkup(String message) {
        return _errorWithMarkup(message,Kind.ERROR);
    }

    public static FormValidation warningWithMarkup(String message) {
        return _errorWithMarkup(message,Kind.WARNING);
    }

    public static FormValidation okWithMarkup(String message) {
        return _errorWithMarkup(message,Kind.OK);
    }

    private static FormValidation _errorWithMarkup(final String message, final Kind kind) {
        if(message==null)
            return ok();
        return new FormValidation(kind) {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                // 1x16 spacer needed for IE since it doesn't support min-height
                respond(rsp,"<div class="+ kind.name().toLowerCase() +"><img src='"+
                        req.getContextPath()+ Hudson.RESOURCE_PATH+"/images/none.gif' height=16 width=1>"+
                        message+"</div>");
            }
        };
    }

    public final Kind kind;

    /**
     * Instances should be created via one of the factory methods above.
     * @param kind
     */
    private FormValidation(Kind kind) {
        this.kind = kind;
    }

    /**
     * Sends out an arbitrary HTML fragment as the output.
     */
    protected void respond(StaplerResponse rsp, String html) throws IOException, ServletException {
        rsp.setContentType("text/html");
        rsp.getWriter().print(html);
    }

    /**
     * Singleton instance that represents "OK" without any message. By far the most common case.
     */
    private static final FormValidation OK = new FormValidation(Kind.OK) {
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            respond(rsp,"<div/>");
        }
    };
}
