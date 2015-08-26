/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
package hudson.diagnosis;

import com.google.common.base.Predicate;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AdministrativeMonitor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ManagementLink;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.util.RobustReflectionConverter;
import hudson.util.VersionNumber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import jenkins.model.Jenkins;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Tracks whether any data structure changes were corrected when loading XML,
 * that could be resaved to migrate that data to the new format.
 *
 * @author Alan.Harder@Sun.Com
 */
@Extension
public class OldDataMonitor extends AdministrativeMonitor {
    private static final Logger LOGGER = Logger.getLogger(OldDataMonitor.class.getName());

    private HashMap<SaveableReference,VersionRange> data = new HashMap<SaveableReference,VersionRange>();

    static OldDataMonitor get(Jenkins j) {
        return (OldDataMonitor) j.getAdministrativeMonitor("OldData");
    }

    public OldDataMonitor() {
        super("OldData");
    }

    @Override
    public String getDisplayName() {
        return Messages.OldDataMonitor_DisplayName();
    }

    public boolean isActivated() {
        return !data.isEmpty();
    }

    public Map<Saveable,VersionRange> getData() {
        Map<SaveableReference,VersionRange> _data;
        synchronized (this) {
            _data = new HashMap<SaveableReference,VersionRange>(this.data);
        }
        Map<Saveable,VersionRange> r = new HashMap<Saveable,VersionRange>();
        for (Map.Entry<SaveableReference,VersionRange> entry : _data.entrySet()) {
            Saveable s = entry.getKey().get();
            if (s != null) {
                r.put(s, entry.getValue());
            }
        }
        return r;
    }

    private static void remove(Saveable obj, boolean isDelete) {
        Jenkins j = Jenkins.getInstance();
        if (j != null) {
            OldDataMonitor odm = get(j);
            SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
            try {
                synchronized (odm) {
                    odm.data.remove(referTo(obj));
                    if (isDelete && obj instanceof Job<?,?>)
                        for (Run r : ((Job<?,?>)obj).getBuilds())
                            odm.data.remove(referTo(r));
                }
            } 
            finally {
                SecurityContextHolder.setContext(oldContext);
            }
        }
    }

    // Listeners to remove data here if resaved or deleted in regular Hudson usage

    @Extension
    public static final SaveableListener changeListener = new SaveableListener() {
        @Override
        public void onChange(Saveable obj, XmlFile file) {
            remove(obj, false);
        }
    };

    @Extension
    public static final ItemListener itemDeleteListener = new ItemListener() {
        @Override
        public void onDeleted(Item item) {
            remove(item, true);
        }
    };

    @Extension
    public static final RunListener<Run> runDeleteListener = new RunListener<Run>() {
        @Override
        public void onDeleted(Run run) {
            remove(run, true);
        }
    };

    /**
     * Inform monitor that some data in a deprecated format has been loaded,
     * and converted in-memory to a new structure.
     * @param obj Saveable object; calling save() on this object will persist
     *            the data in its new format to disk.
     * @param version Hudson release when the data structure changed.
     */
    public static void report(Saveable obj, String version) {
        OldDataMonitor odm = get(Jenkins.getInstance());
        synchronized (odm) {
            try {
                SaveableReference ref = referTo(obj);
                VersionRange vr = odm.data.get(ref);
                if (vr != null) vr.add(version);
                else            odm.data.put(ref, new VersionRange(version, null));
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Bad parameter given to OldDataMonitor", ex);
            }
        }
    }

    /**
     * Inform monitor that some data in a deprecated format has been loaded, during
     * XStream unmarshalling when the Saveable containing this object is not available.
     * @param context XStream unmarshalling context
     * @param version Hudson release when the data structure changed.
     */
    public static void report(UnmarshallingContext context, String version) {
        RobustReflectionConverter.addErrorInContext(context, new ReportException(version));
    }

    private static class ReportException extends Exception {
        private String version;
        private ReportException(String version) {
            this.version = version;
        }
    }

    /**
     * Inform monitor that some unreadable data was found while loading.
     * @param obj Saveable object; calling save() on this object will discard the unreadable data.
     * @param errors Exception(s) thrown while loading, regarding the unreadable classes/fields.
     */
    public static void report(Saveable obj, Collection<Throwable> errors) {
        StringBuilder buf = new StringBuilder();
        int i = 0;
        for (Throwable e : errors) {
            if (e instanceof ReportException) {
                report(obj, ((ReportException)e).version);
            } else {
                if (++i > 1) buf.append(", ");
                buf.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }
        }
        if (buf.length() == 0) return;
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            // Startup failed, something is very broken, so report what we can.
            for (Throwable t : errors) {
                LOGGER.log(Level.WARNING, "could not read " + obj + " (and Jenkins did not start up)", t);
            }
            return;
        }
        OldDataMonitor odm = get(j);
        synchronized (odm) {
            SaveableReference ref = referTo(obj);
            VersionRange vr = odm.data.get(ref);
            if (vr != null) vr.extra = buf.toString();
            else            odm.data.put(ref, new VersionRange(null, buf.toString()));
        }
    }

    public static class VersionRange {
        private static VersionNumber currentVersion = Jenkins.getVersion();

        VersionNumber min, max;
        boolean single = true;
        public String extra;

        public VersionRange(String version, String extra) {
            min = max = version != null ? new VersionNumber(version) : null;
            this.extra = extra;
        }

        public void add(String version) {
            VersionNumber ver = new VersionNumber(version);
            if (min==null) { min = max = ver; }
            else {
                if (ver.isOlderThan(min)) { min = ver; single = false; }
                if (ver.isNewerThan(max)) { max = ver; single = false; }
            }
        }

        @Override
        public String toString() {
            return min==null ? "" : min.toString() + (single ? "" : " - " + max.toString());
        }

        /**
         * Does this version range contain a version more than the given number of releases ago?
         * @param threshold Number of releases
         * @return True if the major version# differs or the minor# differs by >= threshold
         */
        public boolean isOld(int threshold) {
            return currentVersion != null && min != null && (currentVersion.digit(0) > min.digit(0)
                    || (currentVersion.digit(0) == min.digit(0)
                    && currentVersion.digit(1) - min.digit(1) >= threshold));
        }
    }

    /**
     * Sorted list of unique max-versions in the data set.  For select list in jelly.
     */
    public synchronized Iterator<VersionNumber> getVersionList() {
        TreeSet<VersionNumber> set = new TreeSet<VersionNumber>();
        for (VersionRange vr : data.values())
            if (vr.max!=null) set.add(vr.max);
        return set.iterator();
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return new HttpRedirect("manage");
        }
    }

    /**
     * Save all or some of the files to persist data in the new forms.
     * Remove those items from the data map.
     */
    @RequirePOST
    public HttpResponse doUpgrade(StaplerRequest req, StaplerResponse rsp) {
        final String thruVerParam = req.getParameter("thruVer");
        final VersionNumber thruVer = thruVerParam.equals("all") ? null : new VersionNumber(thruVerParam);

        saveAndRemoveEntries( new Predicate<Map.Entry<SaveableReference,VersionRange>>() {
            @Override
            public boolean apply(Map.Entry<SaveableReference, VersionRange> entry) {
                VersionNumber version = entry.getValue().max;
                return version != null && (thruVer == null || !version.isNewerThan(thruVer));
            }
        });

        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Save all files containing only unreadable data (no data upgrades), which discards this data.
     * Remove those items from the data map.
     */
    @RequirePOST
    public HttpResponse doDiscard(StaplerRequest req, StaplerResponse rsp) {
        saveAndRemoveEntries( new Predicate<Map.Entry<SaveableReference,VersionRange>>() {
            @Override
            public boolean apply(Map.Entry<SaveableReference, VersionRange> entry) {
                return entry.getValue().max == null;
            }
        });

        return HttpResponses.forwardToPreviousPage();
    }

    private void saveAndRemoveEntries(Predicate<Map.Entry<SaveableReference, VersionRange>> matchingPredicate) {
        /*
         * Note that there a race condition here: we acquire the lock and get localCopy which includes some
         * project (say); then we go through our loop and save that project; then someone POSTs a new
         * config.xml for the project with some old data, causing remove to be called and the project to be
         * added to data (in the new version); then we hit the end of this method and the project is removed
         * from data again, even though it again has old data.
         *
         * In practice this condition is extremely unlikely, and not a major problem even if it
         * does occur: just means the user will be prompted to discard less than they should have been (and
         * would see the warning again after next restart).
         */
        Map<SaveableReference,VersionRange> localCopy = null;
        synchronized (this) {
            localCopy = new HashMap<SaveableReference,VersionRange>(data);
        }

        List<SaveableReference> removed = new ArrayList<SaveableReference>();
        for (Map.Entry<SaveableReference,VersionRange> entry : localCopy.entrySet()) {
            if (matchingPredicate.apply(entry)) {
                Saveable s = entry.getKey().get();
                if (s != null) {
                    try {
                        s.save();
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, "failed to save " + s, x);
                    }
                }
                removed.add(entry.getKey());
            }
        }

        synchronized (this) {
            data.keySet().removeAll(removed);
        }
    }

    public HttpResponse doIndex(StaplerResponse rsp) throws IOException {
        return new HttpRedirect("manage");
    }

    /** Reference to a saveable object that need not actually hold it in heap. */
    private interface SaveableReference {
        @CheckForNull Saveable get();
        // must also define equals, hashCode
    }

    private static SaveableReference referTo(Saveable s) {
        if (s instanceof Run) {
            Job parent = ((Run) s).getParent();
            if (Jenkins.getInstance().getItemByFullName(parent.getFullName()) == parent) {
                return new RunSaveableReference((Run) s);
            }
        }
        return new SimpleSaveableReference(s);
    }

    private static final class SimpleSaveableReference implements SaveableReference {
        private final Saveable instance;
        SimpleSaveableReference(Saveable instance) {
            this.instance = instance;
        }
        @Override public Saveable get() {
            return instance;
        }
        @Override public int hashCode() {
            return instance.hashCode();
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof SimpleSaveableReference && instance.equals(((SimpleSaveableReference) obj).instance);
        }
    }

    // could easily make an ItemSaveableReference, but Jenkins holds all these strongly, so why bother

    private static final class RunSaveableReference implements SaveableReference {
        private final String id;
        RunSaveableReference(Run<?,?> r) {
            id = r.getExternalizableId();
        }
        @Override public Saveable get() {
            try {
                return Run.fromExternalizableId(id);
            } catch (IllegalArgumentException x) {
                // Typically meaning the job or build was since deleted.
                LOGGER.log(Level.FINE, null, x);
                return null;
            }
        }
        @Override public int hashCode() {
            return id.hashCode();
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof RunSaveableReference && id.equals(((RunSaveableReference) obj).id);
        }
    }

    @Extension
    public static class ManagementLinkImpl extends ManagementLink {
        @Override
        public String getIconFileName() {
            return "document.png";
        }

        @Override
        public String getUrlName() {
            return "administrativeMonitor/OldData/";
        }

        @Override
        public String getDescription() {
            return Messages.OldDataMonitor_Description();
        }

        public String getDisplayName() {
            return Messages.OldDataMonitor_DisplayName();
        }
    }
}
