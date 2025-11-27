/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Tom Huybrechts
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

import static hudson.Util.fixEmpty;
import static hudson.util.FormValidation.APPLY_CONTENT_SECURITY_POLICY_HEADERS;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.security.access.AccessDeniedException;

/**
 * Base class that provides the framework for doing on-the-fly form field validation.
 *
 * <p>
 * The {@link #check()} method is to be implemented by derived classes to perform
 * the validation. See hudson-behavior.js 'validated' CSS class and 'checkUrl' attribute.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated as of 1.294
 *      Use {@link FormValidation} as a return value in your check method.
 */
@Deprecated
public abstract class FormFieldValidator {
    public static final Permission CHECK = Jenkins.ADMINISTER;

    protected final StaplerRequest request;
    protected final StaplerResponse response;
    /**
     * Permission to check, or null if this check doesn't require any permission.
     */
    protected final Permission permission;

    /**
     * The object to which the permission is checked against.
     * If {@link #permission} is non-null, must be non-null.
     */
    protected final AccessControlled subject;

    /**
     * @param adminOnly
     *      Pass true to only let admin users to run the check. This is necessary
     *      for security reason, so that unauthenticated user cannot obtain sensitive
     *      information or run a process that may have side-effect.
     */
    protected FormFieldValidator(StaplerRequest request, StaplerResponse response, boolean adminOnly) {
        this(request, response, adminOnly ? Jenkins.get() : null, adminOnly ? CHECK : null);
    }

    /**
     * @deprecated
     *      Use {@link #FormFieldValidator(Permission)} and remove {@link StaplerRequest} and {@link StaplerResponse}
     *      from your "doCheck..." method parameter
     */
    @Deprecated
    protected FormFieldValidator(StaplerRequest request, StaplerResponse response, Permission permission) {
        this(request, response, Jenkins.get(), permission);
    }

    /**
     * @param permission
     *      Permission needed to perform this validation, or null if no permission is necessary.
     */
    protected FormFieldValidator(Permission permission) {
        this(Stapler.getCurrentRequest(), Stapler.getCurrentResponse(), permission);
    }

    /**
     * @deprecated
     *      Use {@link #FormFieldValidator(AccessControlled,Permission)} and remove {@link StaplerRequest} and {@link StaplerResponse}
     *      from your "doCheck..." method parameter
     */
    @Deprecated
    protected FormFieldValidator(StaplerRequest request, StaplerResponse response, AccessControlled subject, Permission permission) {
        this.request = request;
        this.response = response;
        this.subject = subject;
        this.permission = permission;
    }

    protected FormFieldValidator(AccessControlled subject, Permission permission) {
        this(Stapler.getCurrentRequest(), Stapler.getCurrentResponse(), subject, permission);
    }

    /**
     * Runs the validation code.
     */
    public final void process() throws IOException, ServletException {
        if (permission != null)
            try {
                if (subject == null)
                    throw new AccessDeniedException("No subject");
                subject.checkPermission(permission);
            } catch (AccessDeniedException e) {
                // if the user has hudson-wide admin permission, all checks are allowed
                // this is to protect Hudson administrator from broken ACL/SecurityRealm implementation/configuration.
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER))
                    throw e;
            }

        check();
    }

    protected abstract void check() throws IOException, ServletException;

    /**
     * Gets the parameter as a file.
     */
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Not used.")
    protected final File getFileParameter(String paramName) {
        return new File(Util.fixNull(request.getParameter(paramName)));
    }

    /**
     * Sends out an HTML fragment that indicates a success.
     */
    public void ok() throws IOException, ServletException {
        respond("<div/>");
    }

    /**
     * Sends out an arbitrary HTML fragment.
     */
    public void respond(String html) throws IOException, ServletException {
        response.setContentType("text/html");
        response.getWriter().print(html);
    }

    /**
     * Sends out a string error message that indicates an error.
     *
     * @param message
     *      Human readable message to be sent. {@code error(null)}
     *      can be used as {@code ok()}.
     */
    public void error(String message) throws IOException, ServletException {
        errorWithMarkup(message == null ? null : Util.escape(message));
    }

    public void warning(String message) throws IOException, ServletException {
        warningWithMarkup(message == null ? null : Util.escape(message));
    }

    public void ok(String message) throws IOException, ServletException {
        okWithMarkup(message == null ? null : Util.escape(message));
    }

    /**
     * Sends out a string error message that indicates an error,
     * by formatting it with {@link String#format(String, Object[])}
     */
    public void error(String format, Object... args) throws IOException, ServletException {
        error(String.format(format, args));
    }

    public void warning(String format, Object... args) throws IOException, ServletException {
        warning(String.format(format, args));
    }

    public void ok(String format, Object... args) throws IOException, ServletException {
        ok(String.format(format, args));
    }

    /**
     * Sends out an HTML fragment that indicates an error.
     *
     * <p>
     * This method must be used with care to avoid cross-site scripting
     * attack.
     *
     * @param message
     *      Human readable message to be sent. {@code error(null)}
     *      can be used as {@code ok()}.
     */
    public void errorWithMarkup(String message) throws IOException, ServletException {
        _errorWithMarkup(message, "error");
    }

    public void warningWithMarkup(String message) throws IOException, ServletException {
        _errorWithMarkup(message, "warning");
    }

    public void okWithMarkup(String message) throws IOException, ServletException {
        _errorWithMarkup(message, "ok");
    }

    private void _errorWithMarkup(String message, String cssClass) throws IOException, ServletException {
        if (message == null) {
            ok();
        } else {
            response.setContentType("text/html;charset=UTF-8");
            if (APPLY_CONTENT_SECURITY_POLICY_HEADERS) {
                response.setHeader("Content-Security-Policy", "sandbox; default-src 'none';");
            }
            response.getWriter().print("<div class=" + cssClass + ">" +
                    message + "</div>");
        }
    }

    /**
     * Convenient base class for checking the validity of URLs
     *
     * @deprecated as of 1.294
     *      Use {@link FormValidation.URLCheck}
     */
    @Deprecated
    public abstract static class URLCheck extends FormFieldValidator {

        protected URLCheck(StaplerRequest request, StaplerResponse response) {
            // can be used to check the existence of any file in file system
            // or other HTTP URLs inside firewall, so limit this to the admin.
            super(request, response, true);
        }

        /**
         * Opens the given URL and reads text content from it.
         * This method honors Content-type header.
         */
        protected BufferedReader open(URL url) throws IOException {
            // use HTTP content type to find out the charset.
            URLConnection con = ProxyConfiguration.open(url);
            if (con == null) { // TODO is this even permitted by URL.openConnection?
                throw new IOException(url.toExternalForm());
            }
            return new BufferedReader(
                new InputStreamReader(con.getInputStream(), getCharset(con)));
        }

        /**
         * Finds the string literal from the given reader.
         * @return
         *      true if found, false otherwise.
         */
        protected boolean findText(BufferedReader in, String literal) throws IOException {
            String line;
            while ((line = in.readLine()) != null)
                if (line.contains(literal))
                    return true;
            return false;
        }

        /**
         * Calls the {@link #error(String)} method with a reasonable error message.
         * Use this method when the {@link #open(URL)} or {@link #findText(BufferedReader, String)} fails.
         *
         * @param url
         *      Pass in the URL that was connected. Used for error diagnosis.
         */
        protected void handleIOException(String url, IOException e) throws IOException, ServletException {
            // any invalid URL comes here
            if (e.getMessage().equals(url))
                // Sun JRE (and probably others too) often return just the URL in the error.
                error("Unable to connect " + url);
            else
                error(e.getMessage());
        }

        /**
         * Figures out the charset from the content-type header.
         */
        private String getCharset(URLConnection con) {
            for (String t : con.getContentType().split(";")) {
                t = t.trim().toLowerCase(Locale.ENGLISH);
                if (t.startsWith("charset="))
                    return t.substring(8);
            }
            // couldn't find it. HTML spec says default is US-ASCII,
            // but UTF-8 is a better choice since
            // (1) it's compatible with US-ASCII
            // (2) a well-written web applications tend to use UTF-8
            return "UTF-8";
        }
    }

    /**
     * Checks if the given value is an URL to some Hudson's top page.
     * @since 1.192
     */
    public static class HudsonURL extends URLCheck {
        public HudsonURL(StaplerRequest request, StaplerResponse response) {
            super(request, response);
        }

        @Override
        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            if (value == null) { // nothing entered yet
                ok();
                return;
            }

            if (!value.endsWith("/")) value += '/';

            try {
                URL url = new URL(value);
                HttpURLConnection con = openConnection(url);
                con.connect();
                if (con.getResponseCode() != 200
                || con.getHeaderField("X-Hudson") == null) {
                    error(value + " is not Hudson (" + con.getResponseMessage() + ")");
                    return;
                }

                ok();
            } catch (IOException e) {
                handleIOException(value, e);
            }
        }

        @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "Not used.")
        private HttpURLConnection openConnection(URL url) throws IOException {
            return (HttpURLConnection) url.openConnection();
        }
    }

    /**
     * Checks the file mask (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.90.
     * @deprecated as of 1.294. Use {@link FilePath#validateFileMask(String, boolean, boolean)}
     */
    @Deprecated
    public static class WorkspaceFileMask extends FormFieldValidator {
        private final boolean errorIfNotExist;

        public WorkspaceFileMask(StaplerRequest request, StaplerResponse response) {
            this(request, response, true);
        }

        public WorkspaceFileMask(StaplerRequest request, StaplerResponse response, boolean errorIfNotExist) {
            // Require CONFIGURE permission on the job
            super(request, response, request.findAncestorObject(AbstractProject.class), Item.CONFIGURE);
            this.errorIfNotExist = errorIfNotExist;
        }

        @Override
        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            AbstractProject<?, ?> p = (AbstractProject<?, ?>) subject;

            if (value == null || p == null) {
                ok(); // none entered yet, or something is seriously wrong
                return;
            }

            try {
                FilePath ws = getBaseDirectory(p);

                if (ws == null || !ws.exists()) { // no workspace. can't check
                    ok();
                    return;
                }

                String msg = ws.validateAntFileMask(value, FilePath.VALIDATE_ANT_FILE_MASK_BOUND);
                if (errorIfNotExist)     error(msg);
                else                    warning(msg);
            } catch (InterruptedException e) {
                ok(Messages.FormFieldValidator_did_not_manage_to_validate_may_be_too_sl(value));
            }
        }

        /**
         * The base directory from which the path name is resolved.
         */
        protected FilePath getBaseDirectory(AbstractProject<?, ?> p) {
            return p.getSomeWorkspace();
        }
    }

    /**
     * Checks a valid directory name (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.116
     * @deprecated as of 1.294. Use {@link FilePath#validateRelativeDirectory(String, boolean)}
     *      (see javadoc plugin for the example)
     */
    @Deprecated
    public static class WorkspaceDirectory extends WorkspaceFilePath {
        public WorkspaceDirectory(StaplerRequest request, StaplerResponse response, boolean errorIfNotExist) {
            super(request, response, errorIfNotExist, false);
        }

        public WorkspaceDirectory(StaplerRequest request, StaplerResponse response) {
            this(request, response, true);
        }
    }

    /**
     * Checks a valid file name or directory (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.160
     * @deprecated as of 1.294. Use {@link FilePath#validateRelativePath(String, boolean, boolean)}
     */
    @Deprecated
    public static class WorkspaceFilePath extends FormFieldValidator {
        private final boolean errorIfNotExist;
        private final boolean expectingFile;

        public WorkspaceFilePath(StaplerRequest request, StaplerResponse response, boolean errorIfNotExist, boolean expectingFile) {
            // Require CONFIGURE permission on this job
            super(request, response, request.findAncestorObject(AbstractProject.class), Item.CONFIGURE);
            this.errorIfNotExist = errorIfNotExist;
            this.expectingFile = expectingFile;
        }

        @Override
        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            AbstractProject<?, ?> p = (AbstractProject<?, ?>) subject;

            if (value == null || p == null) {
                ok(); // none entered yet, or something is seriously wrong
                return;
            }

            if (value.contains("*")) {
                // a common mistake is to use wildcard
                error("Wildcard is not allowed here");
                return;
            }

            try {
                FilePath ws = getBaseDirectory(p);

                if (ws == null) { // can't check
                    ok();
                    return;
                }

                if (!ws.exists()) { // no workspace. can't check
                    ok();
                    return;
                }

                if (ws.child(value).exists()) {
                    if (expectingFile) {
                        if (!ws.child(value).isDirectory())
                            ok();
                        else
                            error(value + " is not a file");
                    } else {
                        if (ws.child(value).isDirectory())
                            ok();
                        else
                            error(value + " is not a directory");
                    }
                } else {
                    String msg = "No such " + (expectingFile ? "file" : "directory") + ": " + value;
                    if (errorIfNotExist)     error(msg);
                    else                    warning(msg);
                }
            } catch (InterruptedException e) {
                ok(); // couldn't check
            }
        }

        /**
         * The base directory from which the path name is resolved.
         */
        protected FilePath getBaseDirectory(AbstractProject<?, ?> p) {
            return p.getSomeWorkspace();
        }
    }

    /**
     * Checks a valid executable binary (specified in the 'value' query parameter).
     * It has to be either given as a full path to the executable, or else
     * it will be searched in PATH.
     *
     * <p>
     * This file also handles ".exe" omission in Windows --- I thought Windows
     * has actually more generic mechanism for the executable extension omission,
     * so perhaps this needs to be extended to handle that correctly. More info
     * needed.
     *
     * @since 1.124
     * @deprecated as of 1.294. Use {@link FormValidation#validateExecutable(String)}
     */
    @Deprecated
    public static class Executable extends FormFieldValidator {

        public Executable(StaplerRequest request, StaplerResponse response) {
            // Require admin permission
            super(request, response, true);
        }

        @Override
        protected void check() throws IOException, ServletException {
            String exe = fixEmpty(request.getParameter("value"));
            FormFieldValidator.Executable self = this;
            Exception[] exceptions = {null};
            DOSToUnixPathHelper.iteratePath(exe, new DOSToUnixPathHelper.Helper() {
                @Override
                public void ok() {
                    try {
                        self.ok();
                    } catch (Exception e) {
                        exceptions[0] = e;
                    }
                }

                @Override
                public void checkExecutable(File fexe) {
                    try {
                        self.checkExecutable(fexe);
                    } catch (Exception e) {
                        exceptions[0] = e;
                    }
                }

                @Override
                public void error(String string) {
                    try {
                        self.error(string);
                    } catch (Exception e) {
                        exceptions[0] = e;
                    }
                }

                @Override
                public void validate(File fexe) {
                    try {
                        self.checkExecutable(fexe);
                    } catch (IOException | ServletException ex) {
                        exceptions[0] = ex;
                    }
                }
            });
            Exception e = exceptions[0];
            if (e != null) {
                if (e instanceof IOException)
                    throw (IOException) e;
                if (e instanceof ServletException)
                    throw (ServletException) e;
            }
        }

        /**
         * Provides an opportunity for derived classes to do additional checks on the executable.
         */
        protected void checkExecutable(File exe) throws IOException, ServletException {
            ok();
        }
    }

    /**
     * Verifies that the 'value' parameter is correct base64 encoded text.
     *
     * @since 1.257
     * @deprecated as of 1.305
     *      Use {@link FormValidation#validateBase64(String, boolean, boolean, String)} instead.
     */
    @Deprecated
    public static class Base64 extends FormFieldValidator {
        private final boolean allowWhitespace;
        private final boolean allowEmpty;
        private final String errorMessage;

        public Base64(StaplerRequest request, StaplerResponse response, boolean allowWhitespace, boolean allowEmpty, String errorMessage) {
            super(request, response, false);
            this.allowWhitespace = allowWhitespace;
            this.allowEmpty = allowEmpty;
            this.errorMessage = errorMessage;
        }

        @Override
        protected void check() throws IOException, ServletException {
            try {
                String v = request.getParameter("value");
                if (!allowWhitespace) {
                    if (v.indexOf(' ') >= 0 || v.indexOf('\n') >= 0) {
                        fail();
                        return;
                    }
                }
                v = v.trim();
                if (!allowEmpty && v.isEmpty()) {
                    fail();
                    return;
                }

                java.util.Base64.getDecoder().decode(v);
                ok();
            } catch (IOException | IllegalArgumentException e) {
                fail();
            }
        }

        protected void fail() throws IOException, ServletException {
            error(errorMessage);
        }
    }
}
