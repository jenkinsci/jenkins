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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Main;
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
import hudson.security.ACLContext;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Tracks whether any data structure changes were corrected when loading XML,
 * that could be resaved to migrate that data to the new format.
 *
 * @author Alan.Harder@Sun.Com
 */
@Extension @Symbol("oldData")
public class OldDataMonitor extends AdministrativeMonitor {
    private static final Logger LOGGER = Logger.getLogger(OldDataMonitor.class.getName());

    private ConcurrentMap<SaveableReference, VersionRange> data = new ConcurrentHashMap<>();

    /**
     * Gets instance of the monitor.
     * @param j Jenkins instance
     * @return Monitor instance
     * @throws IllegalStateException Monitor not found.
     *              It should never happen since the monitor is located in the core.
     */
    @NonNull
    static OldDataMonitor get(Jenkins j) throws IllegalStateException {
        return ExtensionList.lookupSingleton(OldDataMonitor.class);
    }

    public OldDataMonitor() {
        super("OldData");
    }

    @Override
    public String getDisplayName() {
        return Messages.OldDataMonitor_DisplayName();
    }

    @Override
    public boolean isActivated() {
        return !data.isEmpty();
    }

    public Map<Saveable, VersionRange> getData() {
        Map<Saveable, VersionRange> r = new HashMap<>();
        for (Map.Entry<SaveableReference, VersionRange> entry : this.data.entrySet()) {
            Saveable s = entry.getKey().get();
            if (s != null) {
                r.put(s, entry.getValue());
            }
        }
        return r;
    }

    private static void remove(Saveable obj, boolean isDelete) {
        Jenkins j = Jenkins.get();
        OldDataMonitor odm = get(j);
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            odm.data.remove(referTo(obj));
            if (isDelete && obj instanceof Job<?, ?>) {
                for (Run r : ((Job<?, ?>) obj).getBuilds()) {
                    odm.data.remove(referTo(r));
                }
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
    public static final RunListener<Run> runDeleteListener = new RunListener<>() {
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
        OldDataMonitor odm = get(Jenkins.get());
        try {
            SaveableReference ref = referTo(obj);
            while (true) {
                VersionRange vr = odm.data.get(ref);
                if (vr != null && odm.data.replace(ref, vr, new VersionRange(vr, version, null))) {
                    break;
                } else if (odm.data.putIfAbsent(ref, new VersionRange(null, version, null)) == null) {
                    break;
                }
            }
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Bad parameter given to OldDataMonitor", ex);
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
                report(obj, ((ReportException) e).version);
            } else {
                if (Main.isUnitTest) {
                    LOGGER.log(Level.INFO, "Trouble loading " + obj, e);
                }
                if (++i > 1) buf.append(", ");
                buf.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }
        }
        if (buf.isEmpty()) return;
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) { // Need this path, at least for unit tests, but also in case of very broken startup
            // Startup failed, something is very broken, so report what we can.
            for (Throwable t : errors) {
                LOGGER.log(Level.WARNING, "could not read " + obj + " (and Jenkins did not start up)", t);
            }
            return;
        }
        OldDataMonitor odm = get(j);
        SaveableReference ref = referTo(obj);
        while (true) {
            VersionRange vr = odm.data.get(ref);
            if (vr != null && odm.data.replace(ref, vr, new VersionRange(vr, null, buf.toString()))) {
                break;
            } else if (odm.data.putIfAbsent(ref, new VersionRange(null, null, buf.toString())) == null) {
                break;
            }
        }
    }

    public static class VersionRange {
        private static VersionNumber currentVersion = Jenkins.getVersion();

        final VersionNumber min;
        final VersionNumber max;
        final boolean single;
        public final String extra;

        public VersionRange(VersionRange previous, String version, String extra) {
            if (previous == null) {
                min = max = version != null ? new VersionNumber(version) : null;
                this.single = true;
                this.extra = extra;
            } else if (version == null) {
                min = previous.min;
                max = previous.max;
                single = previous.single;
                this.extra = extra;
            } else {
                VersionNumber ver = new VersionNumber(version);
                if (previous.min == null || ver.isOlderThan(previous.min)) {
                    this.min = ver;
                } else {
                    this.min = previous.min;
                }
                if (previous.max == null || ver.isNewerThan(previous.max)) {
                    this.max = ver;
                } else {
                    this.max = previous.max;
                }
                this.single = this.max.isNewerThan(this.min);
                this.extra = extra;
            }
        }

        @Override
        public String toString() {
            return min == null ? "" : min + (single ? "" : " - " + max.toString());
        }

        /**
         * Does this version range contain a version more than the given number of releases ago?
         * @param threshold Number of releases
         * @return True if the major version# differs or the minor# differs by ≥ threshold
         */
        public boolean isOld(int threshold) {
            return currentVersion != null && min != null && (currentVersion.getDigitAt(0) > min.getDigitAt(0)
                    || (currentVersion.getDigitAt(0) == min.getDigitAt(0)
                    && currentVersion.getDigitAt(1) - min.getDigitAt(1) >= threshold));
        }

    }

    /**
     * Sorted list of unique max-versions in the data set.  For select list in jelly.
     */
    @Restricted(NoExternalUse.class)
    public Iterator<VersionNumber> getVersionList() {
        TreeSet<VersionNumber> set = new TreeSet<>();
        for (VersionRange vr : data.values()) {
            if (vr.max != null) {
                set.add(vr.max);
            }
        }
        return set.iterator();
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public HttpResponse doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
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
    public HttpResponse doUpgrade(StaplerRequest2 req, StaplerResponse2 rsp) {
        final String thruVerParam = req.getParameter("thruVer");
        final VersionNumber thruVer = thruVerParam.equals("all") ? null : new VersionNumber(thruVerParam);

        saveAndRemoveEntries(entry -> {
            VersionNumber version = entry.getValue().max;
            return version != null && (thruVer == null || !version.isNewerThan(thruVer));
        });

        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Save all files containing only unreadable data (no data upgrades), which discards this data.
     * Remove those items from the data map.
     */
    @RequirePOST
    public HttpResponse doDiscard(StaplerRequest2 req, StaplerResponse2 rsp) {
        saveAndRemoveEntries(entry -> entry.getValue().max == null);

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
        List<SaveableReference> removed = new ArrayList<>();
        for (Map.Entry<SaveableReference, VersionRange> entry : data.entrySet()) {
            if (matchingPredicate.test(entry)) {
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

        data.keySet().removeAll(removed);
    }

    public HttpResponse doIndex(StaplerResponse2 rsp) throws IOException {
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
            if (Jenkins.get().getItemByFullName(parent.getFullName()) == parent) {
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

        RunSaveableReference(Run<?, ?> r) {
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

    @Extension @Symbol("oldData")
    public static class ManagementLinkImpl extends ManagementLink {
        @NonNull
        @Override
        public Category getCategory() {
            return Category.TROUBLESHOOTING;
        }

        @Override
        public String getIconFileName() {
            return "symbol-trash-bin";
        }

        @Override
        public String getUrlName() {
            return "administrativeMonitor/OldData/";
        }

        @Override
        public String getDescription() {
            return Messages.OldDataMonitor_Description();
        }

        @Override
        public String getDisplayName() {
            return Messages.OldDataMonitor_DisplayName();
        }

        @Override
        public Badge getBadge() {
            int size = get(Jenkins.get()).data.size();
            if (size > 0) {
                return new Badge(Integer.toString(size), Messages.OldDataMonitor_OldDataTooltip(), Badge.Severity.WARNING);
            }
            return null;
        }
    }
}
