package hudson.util;

import hudson.FilePath;
import hudson.Util;
import static hudson.Util.fixEmpty;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Base class that provides the framework for doing on-the-fly form field validation.
 *
 * <p>
 * The {@link #check()} method is to be implemented by derived classes to perform
 * the validation. See hudson-behavior.js 'validated' CSS class and 'checkUrl' attribute.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FormFieldValidator {
    protected final StaplerRequest request;
    protected final StaplerResponse response;
    private final boolean isAdminOnly;

    /**
     *
     * @param adminOnly
     *      Pass true to only let admin users to run the check. This is necessary
     *      for security reason, so that unauthenticated user cannot obtain sensitive
     *      information or run a process that may have side-effect.
     */
    protected FormFieldValidator(StaplerRequest request, StaplerResponse response, boolean adminOnly) {
        this.request = request;
        this.response = response;
        isAdminOnly = adminOnly;
    }

    /**
     * Runs the validation code.
     */
    public final void process() throws IOException, ServletException {
        if(isAdminOnly && !Hudson.adminCheck(request,response))
            return; // failed check

        check();
    }

    protected abstract void check() throws IOException, ServletException;

    /**
     * Gets the parameter as a file.
     */
    protected final File getFileParameter(String paramName) {
        return new File(Util.fixNull(request.getParameter(paramName)));
    }

    /**
     * Sends out an HTML fragment that indicates a success.
     */
    public void ok() throws IOException, ServletException {
        response.setContentType("text/html");
        response.getWriter().print("<div/>");
    }

    /**
     * Sends out a string error message that indicates an error.
     *
     * @param message
     *      Human readable message to be sent. <tt>error(null)</tt>
     *      can be used as <tt>ok()</tt>.
     */
    public void error(String message) throws IOException, ServletException {
        errorWithMarkup(message==null?null:Util.escape(message));
    }

    public void warning(String message) throws IOException, ServletException {
        warningWithMarkup(message==null?null:Util.escape(message));
    }
    
    /**
     * Sends out a string error message that indicates an error,
     * by formatting it with {@link String#format(String, Object[])}
     */
    public void error(String format, Object... args) throws IOException, ServletException {
        error(String.format(format,args));
    }

    public void warning(String format, Object... args) throws IOException, ServletException {
        warning(String.format(format,args));
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
    public void errorWithMarkup(String message) throws IOException, ServletException {
        _errorWithMarkup(message,"error");
    }

    public void warningWithMarkup(String message) throws IOException, ServletException {
        _errorWithMarkup(message,"warning");
    }

    private void _errorWithMarkup(String message, String cssClass) throws IOException, ServletException {
        if(message==null) {
            ok();
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().print("<div class="+ cssClass +">"+message+"</div>");
        }
    }

    /**
     * Convenient base class for checking the validity of URLs 
     */
    public static abstract class URLCheck extends FormFieldValidator {

        public URLCheck(StaplerRequest request, StaplerResponse response) {
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
            URLConnection con = url.openConnection();
            if (con == null) { // XXX is this even permitted by URL.openConnection?
                throw new IOException(url.toExternalForm());
            }
            return new BufferedReader(
                new InputStreamReader(con.getInputStream(),getCharset(con)));
        }

        /**
         * Finds the string literal from the given reader.
         * @return
         *      true if found, false otherwise.
         */
        protected boolean findText(BufferedReader in, String literal) throws IOException {
            String line;
            while((line=in.readLine())!=null)
                if(line.indexOf(literal)!=-1)
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
            if(e.getMessage().equals(url))
                // Sun JRE (and probably others too) often return just the URL in the error.
                error("Unable to connect "+url);
            else
                error(e.getMessage());
        }

        /**
         * Figures out the charset from the content-type header.
         */
        private String getCharset(URLConnection con) {
            for( String t : con.getContentType().split(";") ) {
                t = t.trim().toLowerCase();
                if(t.startsWith("charset="))
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
     * Checks the file mask (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.90.
     */
    public static class WorkspaceFileMask extends FormFieldValidator {

        public WorkspaceFileMask(StaplerRequest request, StaplerResponse response) {
            super(request, response, false);
        }

        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(request.getParameter("job"),AbstractProject.class);

            if(value==null || p==null) {
                ok(); // none entered yet, or something is seriously wrong
                return;
            }

            try {
                FilePath ws = p.getWorkspace();

                if(ws==null || !ws.exists()) {// no workspace. can't check
                    ok();
                    return;
                }

                error(ws.validateAntFileMask(value));
            } catch (InterruptedException e) {
                ok(); // coundn't check
            }
        }
    }

    /**
     * Checks a valid directory name (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.116.
     */
    public static class WorkspaceDirectory extends FormFieldValidator {

        public WorkspaceDirectory(StaplerRequest request, StaplerResponse response) {
            super(request, response, false);
        }

        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(request.getParameter("job"),AbstractProject.class);

            if(value==null || p==null) {
                ok(); // none entered yet, or something is seriously wrong
                return;
            }

            if(value.contains("*")) {
                // a common mistake is to use wildcard
                error("Wildcard is not allowed here");
                return;
            }

            try {
                FilePath ws = p.getWorkspace();

                if(!ws.exists()) {// no workspace. can't check
                    ok();
                    return;
                }

                if(ws.child(value).exists()) {
                    if(ws.child(value).isDirectory())
                        ok();
                    else
                        error(value+" is not a directory");
                } else
                    error("No such directory: "+value);
            } catch (InterruptedException e) {
                ok(); // coundn't check
            }
        }
    }

    /**
     * Checks a valid executable binary (specified in the 'value' query parameter)
     *
     * @since 1.124
     */
    public static class Executable extends FormFieldValidator {

        public Executable(StaplerRequest request, StaplerResponse response) {
            super(request, response, true);
        }

        protected void check() throws IOException, ServletException {
            String exe = fixEmpty(request.getParameter("value"));
            if(exe==null) {
                ok(); // nothing entered yet
                return;
            }

            if(exe.indexOf(File.separatorChar)>=0) {
                // this is full path
                File f = new File(exe);
                if(f.exists()) {
                    checkExecutable(f);
                } else {
                    error("There's no such file: "+exe);
                }
            } else {
                // can't really check
                ok();
            }
        }

        /**
         * Provides an opportunity for derived classes to do additional checks on the executable.
         */
        protected void checkExecutable(File exe) throws IOException, ServletException {
            ok();
        }
    }
}
