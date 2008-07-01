package hudson;

import hudson.maven.ExecutedMojo;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.search.SearchableModelObject;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.Area;
import hudson.util.Iterators;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.apache.commons.jexl.util.Introspector;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

/**
 * Utility functions used in views.
 *
 * <p>
 * An instance of this class is created for each request and made accessible
 * from view pages via the variable 'h' (h stands for Hudson.)
 *
 * @author Kohsuke Kawaguchi
 */
public class Functions {
    private static volatile int globalIota = 0;
    private int iota;

    public Functions() {
        iota = globalIota;
        // concurrent requests can use the same ID --- we are just trying to
        // prevent the same user from seeing the same ID repeatedly.
        globalIota+=1000;
    }

    /**
     * Generates an unique ID.
     */
    public String generateId() {
        return "id"+iota++;
    }

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER.format(cal.getTime());
    }

    public static String rfc822Date(Calendar cal) {
        return Util.RFC822_DATETIME_FORMATTER.format(cal.getTime());
    }

    /**
     * Prints the integer as a string that represents difference,
     * like "-5", "+/-0", "+3".
     */
    public static String getDiffString(int i) {
        if(i==0)    return "\u00B10";   // +/-0
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString(int)} that doesn't show anything for +/-0
     */
    public static String getDiffString2(int i) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString2(int)} that puts the result into prefix and suffix
     * if there's something to print
     */
    public static String getDiffString2(String prefix, int i, String suffix) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return prefix+"+"+s+suffix;
        else        return prefix+s+suffix;
    }

    /**
     * Adds the proper suffix.
     */
    public static String addSuffix(int n, String singular, String plural) {
        StringBuffer buf = new StringBuffer();
        buf.append(n).append(' ');
        if(n==1)
            buf.append(singular);
        else
            buf.append(plural);
        return buf.toString();
    }

    public static RunUrl decompose(StaplerRequest req) {
        List<Ancestor> ancestors = req.getAncestors();

        // find the first and last Run instances
        Ancestor f=null,l=null;
        for (Ancestor anc : ancestors) {
            if(anc.getObject() instanceof Run) {
                if(f==null) f=anc;
                l=anc;
            }
        }
        if(l==null) return null;    // there was no Run object

        String head = f.getPrev().getUrl()+'/';
        String base = l.getUrl();

        String reqUri = req.getOriginalRequestURI();
        // despite the spec saying this string is not decoded,
        // Tomcat apparently decodes this string. You see ' ' instead of '%20', which is what
        // the browser has sent. So do some quick scan to see if it's ASCII safe, and if not
        // re-encode it. Otherwise it won't match with ancUrl.
        if(reqUri.indexOf(' ')>=0) {
            try {
                // 3 arg version accepts illegal character. 1-arg version doesn't
                reqUri = new URI(null,reqUri,null).toASCIIString();
            } catch (URISyntaxException e) {
                // try to use reqUri as is.
            }
        }

        String rest = reqUri.substring(f.getUrl().length());

        return new RunUrl( (Run) f.getObject(), head, base, rest);
    }

    /**
     * If we know the user's screen resolution, return it. Otherwise null.
     * @since 1.213
     */
    public static Area getScreenResolution() {
        Cookie res = Functions.getCookie(Stapler.getCurrentRequest(),"screenResolution");
        if(res!=null)
            return Area.parse(res.getValue());
        return null;
    }

    /**
     * URL decomposed for easier computation of relevant URLs.
     *
     * <p>
     * The decomposed URL will be of the form:
     * <pre>
     * aaaaaa/524/bbbbb/cccc
     * -head-| N |---rest---
     * ----- base -----|
     * </pre>
     *
     * <p>
     * The head portion is the part of the URL from the {@link Hudson}
     * object to the first {@link Run} subtype. When "next/prev build"
     * is chosen, this part remains intact.
     *
     * <p>
     * The <tt>524</tt> is the path from {@link Job} to {@link Run}.
     *
     * <p>
     * The <tt>bbb</tt> portion is the path after that till the last
     * {@link Run} subtype. The <tt>ccc</tt> portion is the part
     * after that.
     */
    public static final class RunUrl {
        private final String head, base, rest;
        private final Run run;


        public RunUrl(Run run, String head, String base, String rest) {
            this.run = run;
            this.head = head;
            this.base = base;
            this.rest = rest;
        }

        public String getBaseUrl() {
            return base;
        }

        /**
         * Returns the same page in the next build.
         */
        public String getNextBuildUrl() {
            return getUrl(run.getNextBuild());
        }

        /**
         * Returns the same page in the previous build.
         */
        public String getPreviousBuildUrl() {
            return getUrl(run.getPreviousBuild());
        }

        private String getUrl(Run n) {
            if(n ==null)
                return null;
            else {
                return head+n.getNumber()+rest;
            }
        }
    }

    public static Node.Mode[] getNodeModes() {
        return Node.Mode.values();
    }

    public static String getProjectListString(List<Project> projects) {
        return Items.toNameList(projects);
    }

    public static Object ifThenElse(boolean cond, Object thenValue, Object elseValue) {
        return cond ? thenValue : elseValue;
    }

    public static String appendIfNotNull(String text, String suffix, String nullText) {
        return text == null ? nullText : text + suffix;
    }

    public static Map getSystemProperties() {
        return new TreeMap<Object,Object>(System.getProperties());
    }

    public static Map getEnvVars() {
        return new TreeMap<String,String>(EnvVars.masterEnvVars);
    }

    public static boolean isWindows() {
        return File.pathSeparatorChar==';';
    }

    public static List<LogRecord> getLogRecords() {
        return Hudson.logRecords;
    }

    public static String printLogRecord(LogRecord r) {
        return formatter.format(r);
    }

    public static Cookie getCookie(HttpServletRequest req,String name) {
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static String getCookie(HttpServletRequest req,String name, String defaultValue) {
        Cookie c = getCookie(req, name);
        if(c==null || c.getValue()==null) return defaultValue;
        return c.getValue();
    }

    /**
     * Gets the suffix to use for YUI JavaScript.
     */
    public static String getYuiSuffix() {
        return DEBUG_YUI ? "debug" : "min";
    }

    /**
     * Set to true if you need to use the debug version of YUI.
     */
    public static boolean DEBUG_YUI = System.getProperty("debug.YUI")!=null;

    /**
     * Creates a sub map by using the given range (both ends inclusive).
     */
    public static <V> SortedMap<Integer,V> filter(SortedMap<Integer,V> map, String from, String to) {
        if(from==null && to==null)      return map;
        if(to==null)
            return map.headMap(Integer.parseInt(from)-1);
        if(from==null)
            return map.tailMap(Integer.parseInt(to));

        return map.subMap(Integer.parseInt(to),Integer.parseInt(from)-1);
    }

    private static final SimpleFormatter formatter = new SimpleFormatter();

    /**
     * Used by <tt>layout.jelly</tt> to control the auto refresh behavior.
     *
     * @param noAutoRefresh
     *      On certain pages, like a page with forms, will have annoying interference
     *      with auto refresh. On those pages, disable auto-refresh.
     */
    public static void configureAutoRefresh(HttpServletRequest request, HttpServletResponse response, boolean noAutoRefresh) {
        if(noAutoRefresh)
            return;

        String param = request.getParameter("auto_refresh");
        boolean refresh = isAutoRefresh(request);
        if (param != null) {
            refresh = Boolean.parseBoolean(param);
            Cookie c = new Cookie("hudson_auto_refresh", Boolean.toString(refresh));
            // Need to set path or it will not stick from e.g. a project page to the dashboard.
            // Using request.getContextPath() might work but it seems simpler to just use the hudson_ prefix
            // to avoid conflicts with any other web apps that might be on the same machine.
            c.setPath("/");
            c.setMaxAge(60*60*24*30); // persist it roughly for a month
            response.addCookie(c);
        }
        if (refresh) {
            response.addHeader("Refresh", "10");
        }
    }

    public static boolean isAutoRefresh(HttpServletRequest request) {
        String param = request.getParameter("auto_refresh");
        if (param != null) {
            return Boolean.parseBoolean(param);
        }
        Cookie[] cookies = request.getCookies();
        if(cookies==null)
            return false; // when API design messes it up, we all suffer

        for (Cookie c : cookies) {
            if (c.getName().equals("hudson_auto_refresh")) {
                return Boolean.parseBoolean(c.getValue());
            }
        }
        return false;
    }

    /**
     * Finds the given object in the ancestor list and returns its URL.
     * This is used to determine the "current" URL assigned to the given object,
     * so that one can compute relative URLs from it.
     */
    public static String getNearestAncestorUrl(StaplerRequest req,Object it) {
        List list = req.getAncestors();
        for( int i=list.size()-1; i>=0; i-- ) {
            Ancestor anc = (Ancestor) list.get(i);
            if(anc.getObject()==it)
                return anc.getUrl();
        }
        return null;
    }

    /**
     * Finds the inner-most {@link SearchableModelObject} in scope.
     */
    public static String getSearchURL() {
        List list = Stapler.getCurrentRequest().getAncestors();
        for( int i=list.size()-1; i>=0; i-- ) {
            Ancestor anc = (Ancestor) list.get(i);
            if(anc.getObject() instanceof SearchableModelObject)
                return anc.getUrl()+"/search/";
        }
        return null;
    }

    public static String appendSpaceIfNotNull(String n) {
        if(n==null) return null;
        else        return n+' ';
    }

    public static String getWin32ErrorMessage(IOException e) {
        return Util.getWin32ErrorMessage(e);
    }

    public static boolean isMultiline(String s) {
        if(s==null)     return false;
        return s.indexOf('\r')>=0 || s.indexOf('\n')>=0;
    }

    public static String encode(String s) {
        return Util.encode(s);
    }

    public static String escape(String s) {
        return Util.escape(s);
    }

    public static String xmlEscape(String s) {
        return Util.xmlEscape(s);
    }

    public static void checkPermission(Permission permission) throws IOException, ServletException {
        checkPermission(Hudson.getInstance(),permission);
    }

    public static void checkPermission(AccessControlled object, Permission permission) throws IOException, ServletException {
        if (permission != null) {
            object.checkPermission(permission);
        }
    }

    /**
     * This version is so that the 'checkPermission' on <tt>layout.jelly</tt>
     * degrades gracefully if "it" is not an {@link AccessControlled} object.
     * Otherwise it will perform no check and that problem is hard to notice.
     */
    public static void checkPermission(Object object, Permission permission) throws IOException, ServletException {
        if (permission == null)
            return;
        
        if (object instanceof AccessControlled)
            checkPermission((AccessControlled) object,permission);
        else {
            List<Ancestor> ancs = Stapler.getCurrentRequest().getAncestors();
            for(Ancestor anc : Iterators.reverse(ancs)) {
                Object o = anc.getObject();
                if (o instanceof AccessControlled) {
                    checkPermission((AccessControlled) o,permission);
                    return;
                }
            }
            checkPermission(Hudson.getInstance(),permission);
        }
    }

    /**
     * Returns true if the current user has the given permission.
     *
     * @param permission
     *      If null, returns true. This defaulting is convenient in making the use of this method terse.
     */
    public static boolean hasPermission(Permission permission) throws IOException, ServletException {
        return hasPermission(Hudson.getInstance(),permission);
    }

    public static boolean hasPermission(AccessControlled object, Permission permission) throws IOException, ServletException {
        return permission==null || object.hasPermission(permission);
    }

    public static void adminCheck(StaplerRequest req, StaplerResponse rsp, Object required, Permission permission) throws IOException, ServletException {
        // this is legacy --- all views should be eventually converted to
        // the permission based model.
        if(required!=null && !Hudson.adminCheck(req,rsp)) {
            // check failed. commit the FORBIDDEN response, then abort.
            rsp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            rsp.getOutputStream().close();
            throw new ServletException("Unauthorized access");
        }

        // make sure the user owns the necessary permission to access this page.
        if(permission!=null)
            checkPermission(permission);
    }

    /**
     * Infers the hudson installation URL from the given request.
     */
    public static String inferHudsonURL(StaplerRequest req) {
        String rootUrl = Hudson.getInstance().getRootUrl();
        if(rootUrl !=null)
            // prefer the one explicitly configured, to work with load-balancer, frontend, etc.
            return rootUrl;
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()).append("://");
        buf.append(req.getServerName());
        if(req.getLocalPort()!=80)
            buf.append(':').append(req.getLocalPort());
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Class<? extends Job> clazz) {
        return JobPropertyDescriptor.getPropertyDescriptors(clazz);
    }

    public static List<Descriptor<BuildWrapper>> getBuildWrapperDescriptors(AbstractProject<?,?> project) {
        return BuildWrappers.getFor(project);
    }

    public static List<Descriptor<SecurityRealm>> getSecurityRealmDescriptors() {
        return SecurityRealm.LIST;
    }

    public static List<Descriptor<AuthorizationStrategy>> getAuthorizationStrategyDescriptors() {
        return AuthorizationStrategy.LIST;
    }

    public static List<Descriptor<Builder>> getBuilderDescriptors(AbstractProject<?,?> project) {
        return BuildStepDescriptor.filter(BuildStep.BUILDERS, project.getClass());
    }

    public static List<Descriptor<Publisher>> getPublisherDescriptors(AbstractProject<?,?> project) {
        return BuildStepDescriptor.filter(BuildStep.PUBLISHERS, project.getClass());
    }

    public static List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
        return ComputerLauncher.LIST;
    }

    public static List<Descriptor<RetentionStrategy<?>>> getRetentionStrategyDescriptors() {
        return RetentionStrategy.LIST;
    }

    /**
     * Computes the path to the icon of the given action
     * from the context path.
     */
    public static String getIconFilePath(Action a) {
        String name = a.getIconFileName();
        if(name.startsWith("/"))
            return name.substring(1);
        else
            return "images/24x24/"+name;
    }

    /**
     * Works like JSTL build-in size(x) function,
     * but handle null gracefully.
     */
    public static int size2(Object o) throws Exception {
        if(o==null) return 0;
        return ASTSizeFunction.sizeOf(o,Introspector.getUberspect());
    }

    public static ExecutedMojo.Cache createExecutedMojoCache() {
        return new ExecutedMojo.Cache();
    }

    /**
     * Computes the relative path from the current page to the given item.
     */
    public static String getRelativeLinkTo(Item p) {
        Map<Object,String> ancestors = new HashMap<Object,String>();
        View view=null;

        StaplerRequest request = Stapler.getCurrentRequest();
        for( Ancestor a : request.getAncestors() ) {
            ancestors.put(a.getObject(),a.getRelativePath());
            if(a.getObject() instanceof View)
                view = (View) a.getObject();
        }

        String path = ancestors.get(p);
        if(path!=null)  return path;

        Item i=p;
        String url = "";
        while(true) {
            ItemGroup ig = i.getParent();
            url = i.getShortUrl()+url;

            if(ig==Hudson.getInstance()) {
                assert i instanceof TopLevelItem;
                if(view!=null && view.contains((TopLevelItem)i)) {
                    // if p and the current page belongs to the same view, then return a relative path
                    return ancestors.get(view)+'/'+url;
                } else {
                    // otherwise return a path from the root Hudson
                    return request.getContextPath()+'/'+p.getUrl();
                }
            }

            path = ancestors.get(ig);
            if(path!=null)  return path+'/'+url;

            assert ig instanceof Item; // if not, ig must have been the Hudson instance
            i = (Item) ig;
        }
    }

    public static Map<Thread,StackTraceElement[]> dumpAllThreads() {
        return Thread.getAllStackTraces();
    }

    public static ThreadInfo[] getThreadInfos() {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        return mbean.getThreadInfo(mbean.getAllThreadIds(),mbean.isObjectMonitorUsageSupported(),mbean.isSynchronizerUsageSupported());
    }

    /**
     * Are we running on JRE6 or above?
     */
    public static boolean isMustangOrAbove() {
        try {
            System.console();
            return true;
        } catch(LinkageError e) {
            return false;
        }
    }

    // ThreadInfo.toString() truncates the stack trace by first 8, so needed my own version
    public static String dumpThreadInfo(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
                                             " Id=" + ti.getThreadId() + " " +
                                             ti.getThreadState());
        if (ti.getLockName() != null) {
            sb.append(" on " + ti.getLockName());
        }
        if (ti.getLockOwnerName() != null) {
            sb.append(" owned by \"" + ti.getLockOwnerName() +
                      "\" Id=" + ti.getLockOwnerId());
        }
        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        StackTraceElement[] stackTrace = ti.getStackTrace();
        for (int i=0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && ti.getLockInfo() != null) {
                Thread.State ts = ti.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
       }

       LockInfo[] locks = ti.getLockedSynchronizers();
       if (locks.length > 0) {
           sb.append("\n\tNumber of locked synchronizers = " + locks.length);
           sb.append('\n');
           for (LockInfo li : locks) {
               sb.append("\t- " + li);
               sb.append('\n');
           }
       }
       sb.append('\n');
       return sb.toString();
    }

    public static <T> Collection<T> emptyList() {
        return Collections.emptyList();
    }

    public static String jsStringEscape(String s) {
        StringBuilder buf = new StringBuilder();
        for( int i=0; i<s.length(); i++ ) {
            char ch = s.charAt(i);
            switch(ch) {
            case '\'':
                buf.append("\\'");
                break;
            case '\\':
                buf.append("\\\\");
                break;
            case '"':
                buf.append("\\\"");
                break;
            default:
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    public static String getVersion() {
        return Hudson.VERSION;
    }

    /**
     * Resoruce path prefix.
     */
    public static String getResourcePath() {
        return Hudson.RESOURCE_PATH;
    }

    public static String getViewResource(Object it, String path) {
        Class clazz = it.getClass();

        if(it instanceof Class)
            clazz = (Class)it;
        if(it instanceof Descriptor)
            clazz = ((Descriptor)it).clazz;

        StringBuilder buf = new StringBuilder(Stapler.getCurrentRequest().getContextPath());
        buf.append(Hudson.VIEW_RESOURCE_PATH).append('/');
        buf.append(clazz.getName().replace('.','/').replace('$','/'));
        buf.append('/').append(path);

        return buf.toString();
    }

    /**
     * Can be used to check a checkbox by default.
     * Used from views like {@code h.defaultToTrue(scm.useUpdate)}.
     * The expression will evaluate to true if scm is null.
     */
    public static boolean defaultToTrue(Boolean b) {
        if(b==null) return true;
        return b;
    }

    /**
     * If the value exists, return that value. Otherwise return the default value.
     * <p>
     * This method is useful for supplying a default value to a form field.
     *
     * @since 1.150
     */
    public static <T> T defaulted(T value, T defaultValue) {
        return value!=null ? value : defaultValue;
    }

    public static String printThrowable(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Counts the number of rows needed for textarea to fit the content.
     * Minimum 5 rows.
     */
    public static int determineRows(String s) {
        if(s==null)     return 5;
        return Math.max(5,LINE_END.split(s).length);
    }

    /**
     * Converts the Hudson build status to CruiseControl build status,
     * which is either Success, Failure, Exception, or Unknown.
     */
    public static String toCCStatus(Item i) {
        if (i instanceof Job) {
            Job j = (Job) i;
            switch (j.getIconColor().noAnime()) {
            case ABORTED:
            case RED:
            case YELLOW:
                return "Failure";
            case BLUE:
                return "Success";
            case DISABLED:
            case GREY:
                return "Unknown";
            }
        }
        return "Unknown";
    }

    private static final Pattern LINE_END = Pattern.compile("\r?\n");

    /**
     * Checks if the current user is anonymous.
     */
    public static boolean isAnonymous() {
        return Hudson.getAuthentication() instanceof AnonymousAuthenticationToken;
    }

    /**
     * When called from within JEXL expression evaluation,
     * this method returns the current {@link JellyContext} used
     * to evaluate the script.
     *
     * @since 1.164
     */
    public static JellyContext getCurrentJellyContext() {
        JellyContext context = ExpressionFactory2.CURRENT_CONTEXT.get();
        assert context!=null;
        return context;
    }

    /**
     * Returns a sub-list if the given list is bigger than the specified 'maxSize'
     */
    public static <T> List<T> subList(List<T> base, int maxSize) {
        if(maxSize<base.size())
            return base.subList(0,maxSize);
        else
            return base;
    }

    /**
     * Computes the hyperlink to actions, to handle the situation when the {@link Action#getUrlName()}
     * returns absolute URL.
     */
    public static String getActionUrl(String itUrl,Action action) {
        String urlName = action.getUrlName();

        if(SCHEME.matcher(urlName).matches())
            return urlName; // absolute URL
        else
            // relative URL name
            return Stapler.getCurrentRequest().getContextPath()+'/'+itUrl+urlName;
    }

    /**
     * Escapes the character unsafe for e-mail address.
     * See http://en.wikipedia.org/wiki/E-mail_address for the details,
     * but here the vocabulary is even more restricted.
     */
    public static String toEmailSafeString(String projectName) {
        // TODO: escape non-ASCII characters
        StringBuilder buf = new StringBuilder(projectName.length());
        for( int i=0; i<projectName.length(); i++ ) {
            char ch = projectName.charAt(i);
            if(('a'<=ch && ch<='z')
            || ('z'<=ch && ch<='Z')
            || ('0'<=ch && ch<='9')
            || "-_.".indexOf(ch)>=0)
                buf.append(ch);
            else
                buf.append('_');    // escape
        }
        return projectName;
    }

    public String getSystemProperty(String key) {
        return System.getProperty(key);
    }

    /**
     * Obtains the host name of the Hudson server that clients can use to talk back to.
     * <p>
     * This is primarily used in <tt>slave-agent.jnlp.jelly</tt> to specify the destination
     * that the slaves talk to.
     */
    public String getServerName() {
        // try to infer this from the request URL
        String url = Hudson.getInstance().getRootUrl();
        try {
            if(url!=null) {
                String host = new URL(url).getHost();
                if(host!=null)
                    return host;
            }
        } catch (MalformedURLException e) {
            // fall back to HTTP request
        }
        return Stapler.getCurrentRequest().getServerName();
    }

    private static final Pattern SCHEME = Pattern.compile("[a-z]+://.+");
}
