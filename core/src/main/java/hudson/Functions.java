/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo! Inc., Stephen Connolly, Tom Huybrechts, Alan Harder, Manufacture
 * Francaise des Pneumatiques Michelin, Romain Seguy
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
package hudson;

import hudson.cli.CLICommand;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotatorFactory;
import hudson.init.InitMilestone;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.PageDecorator;
import hudson.model.PaneStatusProperties;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.model.View;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.search.SearchableModelObject;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.GlobalSecurityConfiguration;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.security.captcha.CaptchaSupport;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.UserAvatarResolver;
import hudson.util.Area;
import hudson.util.FormValidation.CheckMethod;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import hudson.util.Iterators;
import hudson.util.jna.GNUCLibrary;
import hudson.util.Secret;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import hudson.widgets.RenderOnDemandClosure;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.GlobalConfigurationCategory.Unclassified;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;

import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.apache.commons.jexl.util.Introspector;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSet;
import org.jvnet.tiger_types.Types;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.jelly.InternationalizedStringExpression.RawHtmlArgument;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.util.RunList;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.CheckForNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility functions used in views.
 *
 * <p>
 * An instance of this class is created for each request and made accessible
 * from view pages via the variable 'h' (h stands for Hudson.)
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("rawtypes")
public class Functions {
    private static final AtomicLong iota = new AtomicLong();

    public Functions() {
    }

    /**
     * Generates an unique ID.
     */
    public String generateId() {
        return "id" + iota.getAndIncrement();
    }

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static boolean isModelWithContextMenu(Object o) {
        return o instanceof ModelObjectWithContextMenu;
    }

    public static boolean isModelWithChildren(Object o) {
        return o instanceof ModelObjectWithChildren;
    }
    
    @Deprecated
    public static boolean isMatrixProject(Object o) {
        return o != null && o.getClass().getName().equals("hudson.matrix.MatrixProject");
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER.format(cal.getTime());
    }

    public static String rfc822Date(Calendar cal) {
        return Util.RFC822_DATETIME_FORMATTER.format(cal.getTime());
    }

    /**
     * During Jenkins start-up, before {@link InitMilestone#PLUGINS_STARTED} the extensions lists will be empty
     * and they are not guaranteed to be fully populated until after {@link InitMilestone#EXTENSIONS_AUGMENTED},
     * similarly, during termination after {@link Jenkins#isTerminating()} is set, it is no longer safe to access
     * the extensions lists.
     * If you attempt to access the extensions list from a UI thread while the extensions are being loaded you will
     * hit a big honking great monitor lock that will block until the effective extension list has been determined
     * (as if a plugin fails to start, all of the failed plugin's extensions and any dependent plugins' extensions
     * will have to be evicted from the list of extensions. In practical terms this only affects the
     * "Jenkins is loading" screen, but as that screen uses the generic layouts we provide this utility method
     * so that the generic layouts can avoid iterating extension lists while Jenkins is starting up.
     * If you attempt to access the extensions list from a UI thread while Jenkins is being shut down, the extensions
     * themselves may no longer be in a valid state and could attempt to revive themselves and block termination.
     * In actual terms the termination only affects those views required to render {@link HudsonIsRestarting}'s
     * {@code index.jelly} which is the same set as the {@link HudsonIsLoading} pages so it makes sense to
     * use both checks here.
     *
     * @return {@code true} if the extensions lists have been populated.
     * @since 1.607
     */
    public static boolean isExtensionsAvailable() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        return jenkins != null && jenkins.getInitLevel().compareTo(InitMilestone.EXTENSIONS_AUGMENTED) >= 0
                && !jenkins.isTerminating();
    }

    public static void initPageVariables(JellyContext context) {
        StaplerRequest currentRequest = Stapler.getCurrentRequest();
        String rootURL = currentRequest.getContextPath();

        Functions h = new Functions();
        context.setVariable("h", h);


        // The path starts with a "/" character but does not end with a "/" character.
        context.setVariable("rootURL", rootURL);

        /*
            load static resources from the path dedicated to a specific version.
            This "/static/VERSION/abc/def.ghi" path is interpreted by stapler to be
            the same thing as "/abc/def.ghi", but this avoids the stale cache
            problem when the user upgrades to new Jenkins. Stapler also sets a long
            future expiration dates for such static resources.

            see https://wiki.jenkins-ci.org/display/JENKINS/Hyperlinks+in+HTML
         */
        context.setVariable("resURL",rootURL+getResourcePath());
        context.setVariable("imagesURL",rootURL+getResourcePath()+"/images");

        context.setVariable("userAgent", currentRequest.getHeader("User-Agent"));
        IconSet.initPageVariables(context);
    }

    /**
     * Given {@code c=MyList (extends ArrayList<Foo>), base=List}, compute the parameterization of 'base'
     * that's assignable from 'c' (in this case {@code List<Foo>}), and return its n-th type parameter
     * (n=0 would return {@code Foo}).
     *
     * <p>
     * This method is useful for doing type arithmetic.
     *
     * @throws AssertionError
     *      if c' is not parameterized.
     */
    public static <B> Class getTypeParameter(Class<? extends B> c, Class<B> base, int n) {
        Type parameterization = Types.getBaseClass(c,base);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            return Types.erasure(Types.getTypeArgument(pt,n));
        } else {
            throw new AssertionError(c+" doesn't properly parameterize "+base);
        }
    }

    public JDK.DescriptorImpl getJDKDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(JDK.DescriptorImpl.class);
    }

    /**
     * Prints the integer as a string that represents difference,
     * like "-5", "+/-0", "+3".
     */
    public static String getDiffString(int i) {
        if(i==0)    return "±0";
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
        StringBuilder buf = new StringBuilder();
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
        // Find "rest" or URI by removing N path components.
        // Not using reqUri.substring(f.getUrl().length()) to avoid mismatches due to
        // url-encoding or extra slashes.  Former may occur in Tomcat (despite the spec saying
        // this string is not decoded, Tomcat apparently decodes this string. You see ' '
        // instead of '%20', which is what the browser has sent), latter may occur in some
        // proxy or URL-rewriting setups where extra slashes are inadvertently added.
        String furl = f.getUrl();
        int slashCount = 0;
        // Count components in ancestor URL
        for (int i = furl.indexOf('/'); i >= 0; i = furl.indexOf('/', i + 1)) slashCount++;
        // Remove that many from request URL, ignoring extra slashes
        String rest = reqUri.replaceFirst("(?:/+[^/]*){" + slashCount + "}", "");

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
     * The head portion is the part of the URL from the {@link jenkins.model.Jenkins}
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

    public static String getProjectListString(List<AbstractProject> projects) {
        return Items.toNameList(projects);
    }

    /**
     * @deprecated as of 1.294
     *      JEXL now supports the real ternary operator "x?y:z", so this work around
     *      is no longer necessary.
     */
    @Deprecated
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
    
    public static boolean isGlibcSupported() {
        try {
            GNUCLibrary.LIBC.getpid();
            return true;
        } catch(Throwable t) {
            return false;
        }
    }

    public static List<LogRecord> getLogRecords() {
        return Jenkins.logRecords;
    }

    public static String printLogRecord(LogRecord r) {
        return formatter.format(r);
    }

    @Restricted(NoExternalUse.class)
    public static String[] printLogRecordHtml(LogRecord r, LogRecord prior) {
        String[] oldParts = prior == null ? new String[4] : logRecordPreformat(prior);
        String[] newParts = logRecordPreformat(r);
        for (int i = 0; i < /* not 4 */3; i++) {
            newParts[i] = "<span class='" + (newParts[i].equals(oldParts[i]) ? "logrecord-metadata-old" : "logrecord-metadata-new") + "'>" + newParts[i] + "</span>";
        }
        newParts[3] = Util.xmlEscape(newParts[3]);
        return newParts;
    }
    /**
     * Partially formats a log record.
     * @return date, source, level, message+thrown
     * @see SimpleFormatter#format(LogRecord)
     */
    private static String[] logRecordPreformat(LogRecord r) {
        String source;
        if (r.getSourceClassName() == null) {
            source = r.getLoggerName();
        } else {
            if (r.getSourceMethodName() == null) {
                source = r.getSourceClassName();
            } else {
                source = r.getSourceClassName() + " " + r.getSourceMethodName();
            }
        }
        String message = new SimpleFormatter().formatMessage(r) + "\n";
        Throwable x = r.getThrown();
        return new String[] {
            String.format("%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp", new Date(r.getMillis())),
            source,
            r.getLevel().getLocalizedName(),
            x == null ? message : message + printThrowable(x) + "\n"
        };
    }

    /**
     * Reverses a collection so that it can be easily walked in reverse order.
     * @since 1.525
     */
    public static <T> Iterable<T> reverse(Collection<T> collection) {
        List<T> list = new ArrayList<T>(collection);
        Collections.reverse(list);
        return list;
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

    private static final Pattern ICON_SIZE = Pattern.compile("\\d+x\\d+");
    @Restricted(NoExternalUse.class)
    public static String validateIconSize(String iconSize) throws SecurityException {
        if (!ICON_SIZE.matcher(iconSize).matches()) {
            throw new SecurityException("invalid iconSize");
        }
        return iconSize;
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
    public static boolean DEBUG_YUI = Boolean.getBoolean("debug.YUI");

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

    /**
     * Creates a sub map by using the given range (upper end inclusive).
     */
    @Restricted(NoExternalUse.class)
    public static <V> SortedMap<Integer,V> filterExcludingFrom(SortedMap<Integer,V> map, String from, String to) {
        if(from==null && to==null)      return map;
        if(to==null)
            return map.headMap(Integer.parseInt(from));
        if(from==null)
            return map.tailMap(Integer.parseInt(to));

        return map.subMap(Integer.parseInt(to),Integer.parseInt(from));
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
            response.addHeader("Refresh", System.getProperty("hudson.Functions.autoRefreshSeconds", "10"));
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

    public static boolean isCollapsed(String paneId) {
    	return PaneStatusProperties.forCurrentUser().isCollapsed(paneId);
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

    /**
     * One nbsp per 10 pixels in given size, which may be a plain number or "NxN"
     * (like an iconSize).  Useful in a sortable table heading.
     */
    public static String nbspIndent(String size) {
        int i = size.indexOf('x');
        i = Integer.parseInt(i > 0 ? size.substring(0, i) : size) / 10;
        StringBuilder buf = new StringBuilder(30);
        for (int j = 0; j < i; j++)
            buf.append("&nbsp;");
        return buf.toString();
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

    public static String xmlUnescape(String s) {
        return s.replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");
    }

    public static String htmlAttributeEscape(String text) {
        StringBuilder buf = new StringBuilder(text.length()+64);
        for( int i=0; i<text.length(); i++ ) {
            char ch = text.charAt(i);
            if(ch=='<')
                buf.append("&lt;");
            else
            if(ch=='>')
                buf.append("&gt;");
            else
            if(ch=='&')
                buf.append("&amp;");
            else
            if(ch=='"')
                buf.append("&quot;");
            else
            if(ch=='\'')
                buf.append("&#39;");
            else
                buf.append(ch);
        }
        return buf.toString();
    }

    public static void checkPermission(Permission permission) throws IOException, ServletException {
        checkPermission(Jenkins.getInstance(),permission);
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
            checkPermission(Jenkins.getInstance(),permission);
        }
    }

    /**
     * Returns true if the current user has the given permission.
     *
     * @param permission
     *      If null, returns true. This defaulting is convenient in making the use of this method terse.
     */
    public static boolean hasPermission(Permission permission) throws IOException, ServletException {
        return hasPermission(Jenkins.getInstance(),permission);
    }

    /**
     * This version is so that the 'hasPermission' can degrade gracefully
     * if "it" is not an {@link AccessControlled} object.
     */
    public static boolean hasPermission(Object object, Permission permission) throws IOException, ServletException {
        if (permission == null)
            return true;
        if (object instanceof AccessControlled)
            return ((AccessControlled)object).hasPermission(permission);
        else {
            List<Ancestor> ancs = Stapler.getCurrentRequest().getAncestors();
            for(Ancestor anc : Iterators.reverse(ancs)) {
                Object o = anc.getObject();
                if (o instanceof AccessControlled) {
                    return ((AccessControlled)o).hasPermission(permission);
                }
            }
            return Jenkins.getInstance().hasPermission(permission);
        }
    }

    public static void adminCheck(StaplerRequest req, StaplerResponse rsp, Object required, Permission permission) throws IOException, ServletException {
        // this is legacy --- all views should be eventually converted to
        // the permission based model.
        if(required!=null && !Hudson.adminCheck(req, rsp)) {
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
        String rootUrl = Jenkins.getInstance().getRootUrl();
        if(rootUrl !=null)
            // prefer the one explicitly configured, to work with load-balancer, frontend, etc.
            return rootUrl;
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()).append("://");
        buf.append(req.getServerName());
        if(! (req.getScheme().equals("http") && req.getLocalPort()==80 || req.getScheme().equals("https") && req.getLocalPort()==443))
            buf.append(':').append(req.getLocalPort());
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    /**
     * Returns the link to be displayed in the footer of the UI.
     */
    public static String getFooterURL() {
        if(footerURL == null) {
            footerURL = System.getProperty("hudson.footerURL");
            if(StringUtils.isBlank(footerURL)) {
                footerURL = "http://jenkins-ci.org/";
            }
        }
        return footerURL;
    }
    private static String footerURL = null;

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Class<? extends Job> clazz) {
        return JobPropertyDescriptor.getPropertyDescriptors(clazz);
    }

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Job job) {
        return DescriptorVisibilityFilter.apply(job, JobPropertyDescriptor.getPropertyDescriptors(job.getClass()));
    }

    public static List<Descriptor<BuildWrapper>> getBuildWrapperDescriptors(AbstractProject<?,?> project) {
        return BuildWrappers.getFor(project);
    }

    public static List<Descriptor<SecurityRealm>> getSecurityRealmDescriptors() {
        return SecurityRealm.all();
    }

    public static List<Descriptor<AuthorizationStrategy>> getAuthorizationStrategyDescriptors() {
        return AuthorizationStrategy.all();
    }

    public static List<Descriptor<Builder>> getBuilderDescriptors(AbstractProject<?,?> project) {
        return BuildStepDescriptor.filter(Builder.all(), project.getClass());
    }

    public static List<Descriptor<Publisher>> getPublisherDescriptors(AbstractProject<?,?> project) {
        return BuildStepDescriptor.filter(Publisher.all(), project.getClass());
    }

    public static List<SCMDescriptor<?>> getSCMDescriptors(AbstractProject<?,?> project) {
        return SCM._for(project);
    }

    public static List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
        return Jenkins.getInstance().<ComputerLauncher,Descriptor<ComputerLauncher>>getDescriptorList(ComputerLauncher.class);
    }

    public static List<Descriptor<RetentionStrategy<?>>> getRetentionStrategyDescriptors() {
        return RetentionStrategy.all();
    }

    public static List<ParameterDescriptor> getParameterDescriptors() {
        return ParameterDefinition.all();
    }

    public static List<Descriptor<CaptchaSupport>> getCaptchaSupportDescriptors() {
        return CaptchaSupport.all();
    }

    public static List<Descriptor<ViewsTabBar>> getViewsTabBarDescriptors() {
        return ViewsTabBar.all();
    }

    public static List<Descriptor<MyViewsTabBar>> getMyViewsTabBarDescriptors() {
        return MyViewsTabBar.all();
    }

    public static List<NodePropertyDescriptor> getNodePropertyDescriptors(Class<? extends Node> clazz) {
        List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
        Collection<NodePropertyDescriptor> list = (Collection) Jenkins.getInstance().getDescriptorList(NodeProperty.class);
        for (NodePropertyDescriptor npd : list) {
            if (npd.isApplicable(clazz)) {
                result.add(npd);
            }
        }
        return result;
    }

    /**
     * Returns those node properties which can be configured as global node properties.
     *
     * @since 1.520
     */
    public static List<NodePropertyDescriptor> getGlobalNodePropertyDescriptors() {
        List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
        Collection<NodePropertyDescriptor> list = (Collection) Jenkins.getInstance().getDescriptorList(NodeProperty.class);
        for (NodePropertyDescriptor npd : list) {
            if (npd.isApplicableAsGlobal()) {
                result.add(npd);
            }
        }
        return result;
    }

    /**
     * Gets all the descriptors sorted by their inheritance tree of {@link Describable}
     * so that descriptors of similar types come nearby.
     *
     * <p>
     * We sort them by {@link Extension#ordinal()} but only for {@link GlobalConfiguration}s,
     * as the value is normally used to compare similar kinds of extensions, and we needed
     * {@link GlobalConfiguration}s to be able to position themselves in a layer above.
     * This however creates some asymmetry between regular {@link Descriptor}s and {@link GlobalConfiguration}s.
     * Perhaps it is better to introduce another annotation element? But then,
     * extensions shouldn't normally concern themselves about ordering too much, and the only reason
     * we needed this for {@link GlobalConfiguration}s are for backward compatibility.
     *
     * @param predicate
     *      Filter the descriptors based on {@link GlobalConfigurationCategory}
     * @since 1.494
     */
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfig(Predicate<GlobalConfigurationCategory> predicate) {
        ExtensionList<Descriptor> exts = ExtensionList.lookup(Descriptor.class);
        List<Tag> r = new ArrayList<Tag>(exts.size());

        for (ExtensionComponent<Descriptor> c : exts.getComponents()) {
            Descriptor d = c.getInstance();
            if (d.getGlobalConfigPage()==null)  continue;

            if (d instanceof GlobalConfiguration) {
                if (predicate.apply(((GlobalConfiguration)d).getCategory()))
                    r.add(new Tag(c.ordinal(), d));
            } else {
                if (predicate.apply(GlobalConfigurationCategory.get(Unclassified.class)))
                    r.add(new Tag(0, d));
            }
        }
        Collections.sort(r);

        List<Descriptor> answer = new ArrayList<Descriptor>(r.size());
        for (Tag d : r) answer.add(d.d);

        return DescriptorVisibilityFilter.apply(Jenkins.getInstance(),answer);
    }

    /**
     * Like {@link #getSortedDescriptorsForGlobalConfig(Predicate)} but with a constant truth predicate, to include all descriptors.
     */
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfig() {
        return getSortedDescriptorsForGlobalConfig(Predicates.<GlobalConfigurationCategory>alwaysTrue());
    }

    /**
     * @deprecated This is rather meaningless.
     */
    @Deprecated
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigNoSecurity() {
        return getSortedDescriptorsForGlobalConfig(Predicates.not(GlobalSecurityConfiguration.FILTER));
    }

    /**
     * Like {@link #getSortedDescriptorsForGlobalConfig(Predicate)} but for unclassified descriptors only.
     * @since 1.506
     */
    public static Collection<Descriptor> getSortedDescriptorsForGlobalConfigUnclassified() {
        return getSortedDescriptorsForGlobalConfig(new Predicate<GlobalConfigurationCategory>() {
            public boolean apply(GlobalConfigurationCategory cat) {
                return cat instanceof GlobalConfigurationCategory.Unclassified;
            }
        });
    }
    
    private static class Tag implements Comparable<Tag> {
        double ordinal;
        String hierarchy;
        Descriptor d;

        Tag(double ordinal, Descriptor d) {
            this.ordinal = ordinal;
            this.d = d;
            this.hierarchy = buildSuperclassHierarchy(d.clazz, new StringBuilder()).toString();
        }

        private StringBuilder buildSuperclassHierarchy(Class c, StringBuilder buf) {
            Class sc = c.getSuperclass();
            if (sc!=null)   buildSuperclassHierarchy(sc,buf).append(':');
            return buf.append(c.getName());
        }

        public int compareTo(Tag that) {
            int r = Double.compare(this.ordinal, that.ordinal);
            if (r!=0)   return -r; // descending for ordinal
            return this.hierarchy.compareTo(that.hierarchy);
        }
    }
    /**
     * Computes the path to the icon of the given action
     * from the context path.
     */
    public static String getIconFilePath(Action a) {
        String name = a.getIconFileName();
        if (name==null)     return null;
        if (name.startsWith("/"))
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
        if(path!=null) {
            return normalizeURI(path + '/');
        }

        Item i=p;
        String url = "";
        while(true) {
            ItemGroup ig = i.getParent();
            url = i.getShortUrl()+url;

            if(ig== Jenkins.getInstance() || (view != null && ig == view.getOwnerItemGroup())) {
                assert i instanceof TopLevelItem;
                if (view != null) {
                    // assume p and the current page belong to the same view, so return a relative path
                    // (even if they did not, View.getItem does not by default verify ownership)
                    return normalizeURI(ancestors.get(view)+'/'+url);
                } else {
                    // otherwise return a path from the root Hudson
                    return normalizeURI(request.getContextPath()+'/'+p.getUrl());
                }
            }

            path = ancestors.get(ig);
            if(path!=null) {
                return normalizeURI(path+'/'+url);
            }

            assert ig instanceof Item; // if not, ig must have been the Hudson instance
            i = (Item) ig;
        }
    }
    
    private static String normalizeURI(String uri) {
        return URI.create(uri).normalize().toString();
    }
    
    /**
     * Gets all the {@link TopLevelItem}s recursively in the {@link ItemGroup} tree.
     * 
     * @since 1.512
     */
    public static List<TopLevelItem> getAllTopLevelItems(ItemGroup root) {
      return Items.getAllItems(root, TopLevelItem.class);
    }
    
    /**
     * Gets the relative name or display name to the given item from the specified group.
     *
     * @since 1.515
     * @param p the Item we want the relative display name
     * @param g the ItemGroup used as point of reference for the item
     * @param useDisplayName if true, returns a display name, otherwise returns a name
     * @return
     *      String like "foo » bar"
     */
    public static String getRelativeNameFrom(Item p, ItemGroup g, boolean useDisplayName) {
        if (p == null) return null;
        if (g == null) return useDisplayName ? p.getFullDisplayName() : p.getFullName();
        String separationString = useDisplayName ? " » " : "/";
        
        // first list up all the parents
        Map<ItemGroup,Integer> parents = new HashMap<ItemGroup,Integer>();
        int depth=0;
        while (g!=null) {
            parents.put(g, depth++);
            if (g instanceof Item)
                g = ((Item)g).getParent();
            else
                g = null;
        }

        StringBuilder buf = new StringBuilder();
        Item i=p;
        while (true) {
            if (buf.length()>0) buf.insert(0,separationString);
            buf.insert(0,useDisplayName ? i.getDisplayName() : i.getName());
            ItemGroup gr = i.getParent();

            Integer d = parents.get(gr);
            if (d!=null) {
                for (int j=d; j>0; j--) {
                    buf.insert(0,separationString);
                    buf.insert(0,"..");
                }
                return buf.toString();
            }

            if (gr instanceof Item)
                i = (Item)gr;
            else
                return null;
        }
    }
    
    /**
     * Gets the name to the given item relative to given group.
     *
     * @since 1.515
     * @param p the Item we want the relative display name
     * @param g the ItemGroup used as point of reference for the item
     * @return
     *      String like "foo/bar"
     */
    public static String getRelativeNameFrom(Item p, ItemGroup g) {
        return getRelativeNameFrom(p, g, false);
    }    
    
    
    /**
     * Gets the relative display name to the given item from the specified group.
     *
     * @since 1.512
     * @param p the Item we want the relative display name
     * @param g the ItemGroup used as point of reference for the item
     * @return
     *      String like "Foo » Bar"
     */
    public static String getRelativeDisplayNameFrom(Item p, ItemGroup g) {
        return getRelativeNameFrom(p, g, true);
    }

    public static Map<Thread,StackTraceElement[]> dumpAllThreads() {
        Map<Thread,StackTraceElement[]> sorted = new TreeMap<Thread,StackTraceElement[]>(new ThreadSorter());
        sorted.putAll(Thread.getAllStackTraces());
        return sorted;
    }

    public static ThreadInfo[] getThreadInfos() {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        return mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(),mbean.isSynchronizerUsageSupported());
    }

    public static ThreadGroupMap sortThreadsAndGetGroupMap(ThreadInfo[] list) {
        ThreadGroupMap sorter = new ThreadGroupMap();
        Arrays.sort(list, sorter);
        return sorter;
    }

    // Common code for sorting Threads/ThreadInfos by ThreadGroup
    private static class ThreadSorterBase {
        protected Map<Long,String> map = new HashMap<Long,String>();

        private ThreadSorterBase() {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) tg = tg.getParent();
            Thread[] threads = new Thread[tg.activeCount()*2];
            int threadsLen = tg.enumerate(threads, true);
            for (int i = 0; i < threadsLen; i++) {
                ThreadGroup group = threads[i].getThreadGroup();
                map.put(threads[i].getId(), group != null ? group.getName() : null);
            }
        }

        protected int compare(long idA, long idB) {
            String tga = map.get(idA), tgb = map.get(idB);
            int result = (tga!=null?-1:0) + (tgb!=null?1:0);  // Will be non-zero if only one is null
            if (result==0 && tga!=null)
                result = tga.compareToIgnoreCase(tgb);
            return result;
        }
    }

    public static class ThreadGroupMap extends ThreadSorterBase implements Comparator<ThreadInfo> {

        /**
         * @return ThreadGroup name or null if unknown
         */
        public String getThreadGroup(ThreadInfo ti) {
            return map.get(ti.getThreadId());
        }

        public int compare(ThreadInfo a, ThreadInfo b) {
            int result = compare(a.getThreadId(), b.getThreadId());
            if (result == 0)
                result = a.getThreadName().compareToIgnoreCase(b.getThreadName());
            return result;
        }
    }

    private static class ThreadSorter extends ThreadSorterBase implements Comparator<Thread> {

        public int compare(Thread a, Thread b) {
            int result = compare(a.getId(), b.getId());
            if (result == 0)
                result = a.getName().compareToIgnoreCase(b.getName());
            return result;
        }
    }

    /**
     * @deprecated Now always true.
     */
    @Deprecated
    public static boolean isMustangOrAbove() {
        return true;
    }

    // ThreadInfo.toString() truncates the stack trace by first 8, so needed my own version
    public static String dumpThreadInfo(ThreadInfo ti, ThreadGroupMap map) {
        String grp = map.getThreadGroup(ti);
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
                                             " Id=" + ti.getThreadId() + " Group=" +
                                             (grp != null ? grp : "?") + " " +
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
            sb.append("\tat ").append(ste);
            sb.append('\n');
            if (i == 0 && ti.getLockInfo() != null) {
                Thread.State ts = ti.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on ").append(ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on ").append(ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on ").append(ti.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked ").append(mi);
                    sb.append('\n');
                }
            }
       }

       LockInfo[] locks = ti.getLockedSynchronizers();
       if (locks.length > 0) {
           sb.append("\n\tNumber of locked synchronizers = " + locks.length);
           sb.append('\n');
           for (LockInfo li : locks) {
               sb.append("\t- ").append(li);
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

    /**
     * Converts "abc" to "Abc".
     */
    public static String capitalize(String s) {
        if(s==null || s.length()==0) return s;
        return Character.toUpperCase(s.charAt(0))+s.substring(1);
    }

    public static String getVersion() {
        return Jenkins.VERSION;
    }

    /**
     * Resoruce path prefix.
     */
    public static String getResourcePath() {
        return Jenkins.RESOURCE_PATH;
    }

    public static String getViewResource(Object it, String path) {
        Class clazz = it.getClass();

        if(it instanceof Class)
            clazz = (Class)it;
        if(it instanceof Descriptor)
            clazz = ((Descriptor)it).clazz;

        StringBuilder buf = new StringBuilder(Stapler.getCurrentRequest().getContextPath());
        buf.append(Jenkins.VIEW_RESOURCE_PATH).append('/');
        buf.append(clazz.getName().replace('.','/').replace('$','/'));
        buf.append('/').append(path);

        return buf.toString();
    }

    public static boolean hasView(Object it, String path) throws IOException {
        if(it==null)    return false;
        return Stapler.getCurrentRequest().getView(it,path)!=null;
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
     * Starting 1.294, JEXL supports the elvis operator "x?:y" that supersedes this.
     *
     * @since 1.150
     */
    public static <T> T defaulted(T value, T defaultValue) {
        return value!=null ? value : defaultValue;
    }

    /**
     * Gets info about the specified {@link Throwable}.
     * @param t Input {@link Throwable}
     * @return If {@link Throwable} is not null, a summary info with the 
     *      stacktrace will be returned. Otherwise, the method returns a default
     *      &quot;No exception details&quot; string.
     */
    public static String printThrowable(@CheckForNull Throwable t) {
        if (t == null) {
            return Messages.Functions_NoExceptionDetails();
        }
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
            switch (j.getIconColor()) {
            case ABORTED:
            case ABORTED_ANIME:
            case RED:
            case RED_ANIME:
            case YELLOW:
            case YELLOW_ANIME:
                return "Failure";
            case BLUE:
            case BLUE_ANIME:
                return "Success";
            case DISABLED:
            case DISABLED_ANIME:
            case GREY:
            case GREY_ANIME:
            case NOTBUILT:
            case NOTBUILT_ANIME:
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
        return Jenkins.getAuthentication() instanceof AnonymousAuthenticationToken;
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
     * Evaluate a Jelly script and return output as a String.
     *
     * @since 1.267
     */
    public static String runScript(Script script) throws JellyTagException {
        StringWriter out = new StringWriter();
        script.run(getCurrentJellyContext(), XMLOutput.createXMLOutput(out));
        return out.toString();
    }

    /**
     * Returns a sub-list if the given list is bigger than the specified {@code maxSize}.
     * <strong>Warning:</strong> do not call this with a {@link RunList}, or you will break lazy loading!
     */
    public static <T> List<T> subList(List<T> base, int maxSize) {
        if(maxSize<base.size())
            return base.subList(0,maxSize);
        else
            return base;
    }

    /**
     * Combine path components via '/' while handling leading/trailing '/' to avoid duplicates.
     */
    public static String joinPath(String... components) {
        StringBuilder buf = new StringBuilder();
        for (String s : components) {
            if (s.length()==0)  continue;

            if (buf.length()>0) {
                if (buf.charAt(buf.length()-1)!='/')
                    buf.append('/');
                if (s.charAt(0)=='/')   s=s.substring(1);
            }
            buf.append(s);
        }
        return buf.toString();
    }

    /**
     * Computes the hyperlink to actions, to handle the situation when the {@link Action#getUrlName()}
     * returns absolute URL.
     */
    public static String getActionUrl(String itUrl,Action action) {
        String urlName = action.getUrlName();
        if(urlName==null)   return null;    // to avoid NPE and fail to render the whole page
        try {
            if (new URI(urlName).isAbsolute()) {
                return urlName;
            }
        } catch (URISyntaxException x) {
            Logger.getLogger(Functions.class.getName()).log(Level.WARNING, "Failed to parse URL for {0}: {1}", new Object[] {action, x});
            return null;
        }
        if(urlName.startsWith("/"))
            return joinPath(Stapler.getCurrentRequest().getContextPath(),urlName);
        else
            // relative URL name
            return joinPath(Stapler.getCurrentRequest().getContextPath()+'/'+itUrl,urlName);
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
        // Try to infer this from the configured root URL.
        // This makes it work correctly when Hudson runs behind a reverse proxy.
        String url = Jenkins.getInstance().getRootUrl();
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

    /**
     * Determines the form validation check URL. See textbox.jelly
     *
     * @deprecated
     *      Use {@link #calcCheckUrl}
     */
    @Deprecated
    public String getCheckUrl(String userDefined, Object descriptor, String field) {
        if(userDefined!=null || field==null)   return userDefined;
        if (descriptor instanceof Descriptor) {
            Descriptor d = (Descriptor) descriptor;
            return d.getCheckUrl(field);
        }
        return null;
    }

    /**
     * Determines the parameters that client-side needs for a form validation check. See prepareDatabinding.jelly
     * @since 1.528
     */
    public void calcCheckUrl(Map attributes, String userDefined, Object descriptor, String field) {
        if(userDefined!=null || field==null)   return;

        if (descriptor instanceof Descriptor) {
            Descriptor d = (Descriptor) descriptor;
            CheckMethod m = d.getCheckMethod(field);
            attributes.put("checkUrl",m.toStemUrl());
            attributes.put("checkDependsOn",m.getDependsOn());
        }
    }

    /**
     * If the given href link is matching the current page, return true.
     *
     * Used in <tt>task.jelly</tt> to decide if the page should be highlighted.
     */
    public boolean hyperlinkMatchesCurrentPage(String href) throws UnsupportedEncodingException {
        String url = Stapler.getCurrentRequest().getRequestURL().toString();
        if (href == null || href.length() <= 1) return ".".equals(href) && url.endsWith("/");
        url = URLDecoder.decode(url,"UTF-8");
        href = URLDecoder.decode(href,"UTF-8");
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (href.endsWith("/")) href = href.substring(0, href.length() - 1);

        return url.endsWith(href);
    }

    public <T> List<T> singletonList(T t) {
        return Collections.singletonList(t);
    }

    /**
     * Gets all the {@link PageDecorator}s.
     */
    public static List<PageDecorator> getPageDecorators() {
        // this method may be called to render start up errors, at which point Hudson doesn't exist yet. see HUDSON-3608 
        if(Jenkins.getInstanceOrNull()==null)  return Collections.emptyList();
        return PageDecorator.all();
    }
    
    public static List<Descriptor<Cloud>> getCloudDescriptors() {
        return Cloud.all();
    }

    /**
     * Prepend a prefix only when there's the specified body.
     */
    public String prepend(String prefix, String body) {
        if(body!=null && body.length()>0)
            return prefix+body;
        return body;
    }

    public static List<Descriptor<CrumbIssuer>> getCrumbIssuerDescriptors() {
        return CrumbIssuer.all();
    }

    public static String getCrumb(StaplerRequest req) {
        Jenkins h = Jenkins.getInstanceOrNull();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.getCrumb(req) : "";
    }

    public static String getCrumbRequestField() {
        Jenkins h = Jenkins.getInstanceOrNull();
        CrumbIssuer issuer = h != null ? h.getCrumbIssuer() : null;
        return issuer != null ? issuer.getDescriptor().getCrumbRequestField() : "";
    }

    public static Date getCurrentTime() {
        return new Date();
    }

    public static Locale getCurrentLocale() {
        Locale locale=null;
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null)
            locale = req.getLocale();
        if(locale==null)
            locale = Locale.getDefault();
        return locale;
    }

    /**
     * Generate a series of &lt;script> tags to include <tt>script.js</tt>
     * from {@link ConsoleAnnotatorFactory}s and {@link ConsoleAnnotationDescriptor}s.
     */
    public static String generateConsoleAnnotationScriptAndStylesheet() {
        String cp = Stapler.getCurrentRequest().getContextPath();
        StringBuilder buf = new StringBuilder();
        for (ConsoleAnnotatorFactory f : ConsoleAnnotatorFactory.all()) {
            String path = cp + "/extensionList/" + ConsoleAnnotatorFactory.class.getName() + "/" + f.getClass().getName();
            if (f.hasScript())
                buf.append("<script src='").append(path).append("/script.js'></script>");
            if (f.hasStylesheet())
                buf.append("<link rel='stylesheet' type='text/css' href='").append(path).append("/style.css' />");
        }
        for (ConsoleAnnotationDescriptor d : ConsoleAnnotationDescriptor.all()) {
            String path = cp+"/descriptor/"+d.clazz.getName();
            if (d.hasScript())
                buf.append("<script src='").append(path).append("/script.js'></script>");
            if (d.hasStylesheet())
                buf.append("<link rel='stylesheet' type='text/css' href='").append(path).append("/style.css' />");
        }
        return buf.toString();
    }

    /**
     * Work around for bug 6935026.
     */
    public List<String> getLoggerNames() {
        while (true) {
            try {
                List<String> r = new ArrayList<String>();
                Enumeration<String> e = LogManager.getLogManager().getLoggerNames();
                while (e.hasMoreElements())
                    r.add(e.nextElement());
                return r;
            } catch (ConcurrentModificationException e) {
                // retry
            }
        }
    }

    /**
     * Used by &lt;f:password/> so that we send an encrypted value to the client.
     */
    public String getPasswordValue(Object o) {
        if (o==null)    return null;
        if (o instanceof Secret)    return ((Secret)o).getEncryptedValue();
        if (getIsUnitTest()) {
            throw new SecurityException("attempted to render plaintext ‘" + o + "’ in password field; use a getter of type Secret instead");
        }
        return o.toString();
    }

    public List filterDescriptors(Object context, Iterable descriptors) {
        return DescriptorVisibilityFilter.apply(context,descriptors);
    }
    
    /**
     * Returns true if we are running unit tests.
     */
    public static boolean getIsUnitTest() {
        return Main.isUnitTest;
    }

    /**
     * Returns {@code true} if the {@link Run#ARTIFACTS} permission is enabled,
     * {@code false} otherwise.
     *
     * <p>When the {@link Run#ARTIFACTS} permission is not turned on using the
     * {@code hudson.security.ArtifactsPermission} system property, this
     * permission must not be considered to be set to {@code false} for every
     * user. It must rather be like if the permission doesn't exist at all
     * (which means that every user has to have an access to the artifacts but
     * the permission can't be configured in the security screen). Got it?</p>
     */
    public static boolean isArtifactsPermissionEnabled() {
        return Boolean.getBoolean("hudson.security.ArtifactsPermission");
    }

    /**
     * Returns {@code true} if the {@link Item#WIPEOUT} permission is enabled,
     * {@code false} otherwise.
     *
     * <p>The "Wipe Out Workspace" action available on jobs is controlled by the
     * {@link Item#BUILD} permission. For some specific projects, however, it is
     * not acceptable to let users have this possibility, even it they can
     * trigger builds. As such, when enabling the {@code hudson.security.WipeOutPermission}
     * system property, a new "WipeOut" permission will allow to have greater
     * control on the "Wipe Out Workspace" action.</p>
     */
    public static boolean isWipeOutPermissionEnabled() {
        return Boolean.getBoolean("hudson.security.WipeOutPermission");
    }

    public static String createRenderOnDemandProxy(JellyContext context, String attributesToCapture) {
        return Stapler.getCurrentRequest().createJavaScriptProxy(new RenderOnDemandClosure(context,attributesToCapture));
    }

    public static String getCurrentDescriptorByNameUrl() {
        return Descriptor.getCurrentDescriptorByNameUrl();
    }
    
    public static String setCurrentDescriptorByNameUrl(String value) {
        String o = getCurrentDescriptorByNameUrl();
        Stapler.getCurrentRequest().setAttribute("currentDescriptorByNameUrl", value);

        return o;
    }

    public static void restoreCurrentDescriptorByNameUrl(String old) {
        Stapler.getCurrentRequest().setAttribute("currentDescriptorByNameUrl", old);
    }

    public static List<String> getRequestHeaders(String name) {
        List<String> r = new ArrayList<String>();
        Enumeration e = Stapler.getCurrentRequest().getHeaders(name);
        while (e.hasMoreElements()) {
            r.add(e.nextElement().toString());
        }
        return r;
    }

    /**
     * Used for arguments to internationalized expressions to avoid escape
     */
    public static Object rawHtml(Object o) {
        return o==null ? null : new RawHtmlArgument(o);
    }

    public static ArrayList<CLICommand> getCLICommands() {
        ArrayList<CLICommand> all = new ArrayList<CLICommand>(CLICommand.all());
        Collections.sort(all, new Comparator<CLICommand>() {
            public int compare(CLICommand cliCommand, CLICommand cliCommand1) {
                return cliCommand.getName().compareTo(cliCommand1.getName());
            }
        });
        return all;
    }

    /**
     * Returns an avatar image URL for the specified user and preferred image size
     * @param user the user
     * @param avatarSize the preferred size of the avatar image
     * @return a URL string
     * @since 1.433
     */
    public static String getAvatar(User user, String avatarSize) {
        return UserAvatarResolver.resolve(user, avatarSize);
    }

    /**
     * @deprecated as of 1.451
     *      Use {@link #getAvatar}
     */
    @Deprecated
    public String getUserAvatar(User user, String avatarSize) {
        return getAvatar(user,avatarSize);
    }
    
    
    /**
     * Returns human readable information about file size
     * 
     * @param size file size in bytes
     * @return file size in appropriate unit
     */
    public static String humanReadableByteSize(long size){
        String measure = "B";
        if(size < 1024){
            return size + " " + measure;
        }
        Double number = new Double(size);
        if(number>=1024){
            number = number/1024;
            measure = "KB";
            if(number>=1024){
                number = number/1024;
                measure = "MB";
                if(number>=1024){
                    number=number/1024;
                    measure = "GB";
                }
            }
        }
        DecimalFormat format = new DecimalFormat("#0.00");
        return format.format(number) + " " + measure;
    }

    /**
     * Get a string that can be safely broken to several lines when necessary.
     *
     * This implementation inserts &lt;wbr> tags into string. It allows browsers
     * to wrap line before any sequence of punctuation characters or anywhere
     * in the middle of prolonged sequences of word characters.
     *
     * @since 1.517
     */
    public static String breakableString(final String plain) {
        if (plain == null) {
            return null;
        }
        return plain.replaceAll("([\\p{Punct}&&[^;]]+\\w)", "<wbr>$1")
                .replaceAll("([^\\p{Punct}\\s-]{20})(?=[^\\p{Punct}\\s-]{10})", "$1<wbr>")
        ;
    }

    /**
     * Advertises the minimum set of HTTP headers that assist programmatic
     * discovery of Jenkins.
     */
    public static void advertiseHeaders(HttpServletResponse rsp) {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j!=null) {
            rsp.setHeader("X-Hudson","1.395");
            rsp.setHeader("X-Jenkins", Jenkins.VERSION);
            rsp.setHeader("X-Jenkins-Session", Jenkins.SESSION_HASH);

            TcpSlaveAgentListener tal = j.tcpSlaveAgentListener;
            if (tal !=null) {
                int p = tal.getAdvertisedPort();
                rsp.setIntHeader("X-Hudson-CLI-Port", p);
                rsp.setIntHeader("X-Jenkins-CLI-Port", p);
                rsp.setIntHeader("X-Jenkins-CLI2-Port", p);
                rsp.setHeader("X-Jenkins-CLI-Host", TcpSlaveAgentListener.CLI_HOST_NAME);
            }
        }
    }

    @Restricted(NoExternalUse.class) // for actions.jelly and ContextMenu.add
    public static boolean isContextMenuVisible(Action a) {
        if (a instanceof ModelObjectWithContextMenu.ContextMenuVisibility) {
            return ((ModelObjectWithContextMenu.ContextMenuVisibility) a).isVisible();
        } else {
            return true;
        }
    }

}
