package jenkins.security;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.init.Initializer;
import hudson.remoting.Callable;
import hudson.util.HttpResponses;
import hudson.util.TextFile;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleSensitive;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.*;

/**
 * Whitelists {@link Callable}s that are approved by the admins.
 *
 *
 * <p>
 * Smaller ordinal value allows other programmable {@link CallableWhitelist} to accept/reject
 * {@link Callable}s without bothering the admins. This impl should be used only for those
 * {@link Callable}s that our program does not have any idea for.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100)
public class AdminCallableWhitelist extends CallableWhitelist implements StaplerProxy {
    private final Set<Class> rejected = Collections.synchronizedSet(new LinkedHashSet<Class>());

    // CoW
    private volatile Set<String> whitelisted = Collections.emptySet();

    @Inject
    Jenkins jenkins;

    @Override
    public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
        String name = subject.getClass().getName();

        if (whitelisted.contains(name))
            return true;    // whitelisted by admin

        // otherwise record the problem and refuse to execute that
        boolean changed = rejected.add(subject.getClass());
        if (changed) {
            saveRejected();
        }

        return false;
    }

    /**
     * Returns the current whitelisted names
     */
    public String getWhitelistText() {
        return StringUtils.join(whitelisted,'\n');
    }

    /**
     * Returns the currently rejected callables.
     */
    public List<RejectedCallable> getRejectedList() {
        List<RejectedCallable> l = new ArrayList<RejectedCallable>();
        synchronized (rejected) {
            for (Class c : rejected) {
                l.add(new RejectedCallable(c));
            }
        }
        return l;
    }

    public boolean hasRejection() {
        return !rejected.isEmpty();
    }

    /**
     * Persists {@link #rejected}
     */
    private void saveRejected() {
        try {
            synchronized (rejected) {
                StringBuilder buf = new StringBuilder();
                for (Class c : rejected) {
                    if (buf.length()>0) buf.append('\n');
                    buf.append(c.getName());
                }
                getRejectedFile().write(buf.toString());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to persist "+getRejectedFile(), e);
        }
    }

    public TextFile getRejectedFile() {
        // while this file is not a secret, write access to this file is dangerous,
        // so put this in the better-protected part of $JENKINS_HOME, which is in secrets/
        return new TextFile(new File(jenkins.getRootDir(),"secrets/rejected-callables.txt"));
    }

    private void updateWhiteList(Collection<String> newWhiteList) throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);

        this.whitelisted = ImmutableSet.copyOf(newWhiteList);
        getWhitelistFile().write(StringUtils.join(whitelisted, "\n"));

        // remove whitelisted ones from the reject list
        boolean changed = false;
        synchronized (rejected) {
            Iterator<Class> itr = rejected.iterator();
            while (itr.hasNext()) {
                Class next = itr.next();
                if (whitelisted.contains(next.getName())) {
                    itr.remove();
                    changed = true;
                }
            }
        }

        if (changed)
            saveRejected();
    }

    public TextFile getWhitelistFile() {
        return new TextFile(new File(jenkins.getRootDir(),"secrets/whitelisted-callables.txt"));
    }

    @RequirePOST
    public HttpResponse doSubmit(StaplerRequest req) throws IOException {
        jenkins.checkPermission(Jenkins.ADMINISTER);

        List<String> names = new ArrayList<String>();
        for (String n : Util.fixNull(req.getParameter("whitelist")).split("\n"))  {
            n=n.trim();
            if (n.length()>0)
                names.add(n);
        }

        Enumeration e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            if (name.startsWith("class:")) {
                names.add(name.substring(6));
            }
        }

        updateWhiteList(names);

        return HttpResponses.redirectToDot();
    }

    /**
     * Approves all the currently rejected subjects
     */
    @RequirePOST
    public HttpResponse doApproveAll() throws IOException {
        synchronized (rejected) {
            List<String> names = new ArrayList<String>();
            for (Class c : rejected) {
                names.add(c.getName());
            }
            approve(names);

            return HttpResponses.ok();
        }
    }

    /**
     * Approves specific callables by their names.
     */
    @RequirePOST
    public HttpResponse doApprove(@QueryParameter String value) throws IOException {
        Set<String> names = new HashSet<String>();
        for (String line : value.split("\n")) {
            line = line.trim();
            if (!line.isEmpty())
                names.add(line);
        }

        approve(names);

        return HttpResponses.ok();
    }

    /**
     * Whitelists specific callables
     */
    public void approve(Collection<String> names) throws IOException {
        Set<String> newWhiteList = new HashSet<String>(whitelisted);
        newWhiteList.addAll(names);
        updateWhiteList(newWhiteList);
    }

    @Initializer(after=EXTENSIONS_AUGMENTED,fatal=false)
    public static void init() throws IOException {
        CallableWhitelist.all().get(AdminCallableWhitelist.class).load();
    }

    public void load() throws IOException {
        if (getRejectedFile().exists()) {
            for (String line : getRejectedFile().read().split("\n")) {
                try {
                    Class<?> c = jenkins.pluginManager.uberClassLoader.loadClass(line.trim());
                    rejected.add(c);
                } catch (ClassNotFoundException e) {
                    // no longer present in the system?
                }
            }
        }

        if (getWhitelistFile().exists()) {
            List<String> whitelist = new ArrayList<String>();
            for (String line : getWhitelistFile().read().split("\n")) {
                whitelist.add(line.trim());
            }
            this.whitelisted = ImmutableSet.copyOf(whitelist);
        }
    }

    /**
     * Restricts the access to administrator.
     */
    @Override
    public Object getTarget() {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    public class RejectedCallable {
        public final Class clazz;

        public RejectedCallable(Class clazz) {
            this.clazz = clazz;
        }

        public @CheckForNull PluginWrapper getPlugin() {
            return jenkins.pluginManager.whichPlugin(clazz);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AdminCallableWhitelist.class.getName());
}
