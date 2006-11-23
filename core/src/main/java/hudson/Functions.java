package hudson;

import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.Hudson;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Calendar;
import java.util.SortedMap;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class Functions {
    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER.format(cal.getTime());
    }

    public static String getDiffString(int i) {
        if(i==0)    return "\u00B10";   // +/-0
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString2(int)} that doesn't show anything for +/-0
     */
    public static String getDiffString2(int i) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
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
        @SuppressWarnings("unchecked") // pre-JDK 5 API?
        List<Ancestor> ancestors = req.getAncestors();
        for (Ancestor anc : ancestors) {
            if(anc.getObject() instanceof Run) {
                // bingo
                String ancUrl = anc.getUrl();

                String reqUri = req.getOriginalRequestURI();

                return new RunUrl(
                    (Run) anc.getObject(), ancUrl,
                    reqUri.substring(ancUrl.length()),
                    req.getContextPath() );
            }
        }
        return null;
    }

    public static final class RunUrl {
        private final String contextPath;
        private final String basePortion;
        private final String rest;
        private final Run run;

        public RunUrl(Run run, String basePortion, String rest, String contextPath) {
            this.run = run;
            this.basePortion = basePortion;
            this.rest = rest;
            this.contextPath = contextPath;
        }

        public String getBaseUrl() {
            return basePortion;
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
                String url = contextPath + '/' + n.getUrl();
                assert url.endsWith("/");
                url = url.substring(0,url.length()-1);  // cut off the trailing '/'
                return url+rest;
            }
        }
    }

    public static Node.Mode[] getNodeModes() {
        return Node.Mode.values();
    }

    public static String getProjectListString(List<Project> projects) {
        return Project.toNameList(projects);
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
}
