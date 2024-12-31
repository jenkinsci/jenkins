package hudson.tools;

import hudson.FilePath;
import hudson.model.DownloadService.Downloadable;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.agents.NodeSpecific;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.sf.json.JSONObject;

/**
 * Partial convenience implementation of {@link ToolInstaller} that just downloads
 * an archive from the URL and extracts it.
 *
 * <p>
 * Each instance of this is configured to download from a specific URL identified by an ID.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.308
 */
public abstract class DownloadFromUrlInstaller extends ToolInstaller {
    public final String id;

    protected DownloadFromUrlInstaller(String id) {
        // this installer implementation is designed for platform independent binary,
        // and as such we don't provide the label support
        super(null);
        this.id = id;
    }

    /**
     * Checks if the specified expected location already contains the installed version of the tool.
     *
     * This check needs to run fairly efficiently. The current implementation uses the source URL of {@link Installable},
     * based on the assumption that released bits do not change its content.
     */
    protected boolean isUpToDate(FilePath expectedLocation, Installable i) throws IOException, InterruptedException {
        FilePath marker = expectedLocation.child(".installedFrom");
        return marker.exists() && marker.readToString().equals(i.url);
    }

    /**
     * Gets the {@link Installable} identified by {@link #id}.
     *
     * @return null if no such ID is found.
     */
    public Installable getInstallable() throws IOException {
        for (Installable i : ((DescriptorImpl<?>) getDescriptor()).getInstallables())
            if (id.equals(i.id))
                return i;
        return null;
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath expected = preferredLocation(tool, node);

        Installable inst = getInstallable();
        if (inst == null) {
            log.getLogger().println("Invalid tool ID " + id);
            return expected;
        }

        if (inst instanceof NodeSpecific) {
            inst = (Installable) ((NodeSpecific) inst).forNode(node, log);
        }

        if (isUpToDate(expected, inst))
            return expected;

        if (expected.installIfNecessaryFrom(new URL(inst.url), log, "Unpacking " + inst.url + " to " + expected + " on " + node.getDisplayName())) {
            expected.child(".timestamp").delete(); // we don't use the timestamp
            FilePath base = findPullUpDirectory(expected);
            if (base != null && base != expected)
                base.moveAllChildrenTo(expected);
            // leave a record for the next up-to-date check
            expected.child(".installedFrom").write(inst.url, "UTF-8");
            expected.act(new ZipExtractionInstaller.ChmodRecAPlusX());
        }

        return expected;
    }

    /**
     * Often an archive contains an extra top-level directory that's unnecessary when extracted on the disk
     * into the expected location. If your installation sources provide that kind of archives, override
     * this method to find the real root location.
     *
     * <p>
     * The caller will "pull up" the discovered real root by throw away the intermediate directory,
     * so that the user-configured "tool home" directory contains the right files.
     *
     * <p>
     * The default implementation applies some heuristics to auto-determine if the pull up is necessary.
     * This should work for typical archive files.
     *
     * @param root
     *      The directory that contains the extracted archive. This directory contains nothing but the
     *      extracted archive. For example, if the user installed
     *      <a href="https://archive.apache.org/dist/ant/binaries/jakarta-ant-1.1.zip">jakarta-ant-1.1.zip</a> , this directory would contain
     *      a single directory "jakarta-ant".
     *
     * @return
     *      Return the real top directory inside {@code root} that contains the meat. In the above example,
     *      {@code root.child("jakarta-ant")} should be returned. If there's no directory to pull up,
     *      return null.
     */
    protected FilePath findPullUpDirectory(FilePath root) throws IOException, InterruptedException {
        // if the directory just contains one directory and that alone, assume that's the pull up subject
        // otherwise leave it as is.
        List<FilePath> children = root.list();
        if (children.size() != 1)    return null;
        if (children.get(0).isDirectory())
            return children.get(0);
        return null;
    }

    public abstract static class DescriptorImpl<T extends DownloadFromUrlInstaller> extends ToolInstallerDescriptor<T> {

        @SuppressWarnings("deprecation") // intentionally adding dynamic item here
        protected DescriptorImpl() {
            Downloadable.all().add(createDownloadable());
        }

        /**
         * function that creates a {@link Downloadable}.
         * @return a downloadable object
         */
        public Downloadable createDownloadable() {
            final DescriptorImpl delegate = this;
            return new Downloadable(getId()) {
                @Override
                public JSONObject reduce(List<JSONObject> jsonList) {
                    if (isDefaultSchema(jsonList)) {
                        return delegate.reduce(jsonList);
                    } else {
                        //if it's not default schema fall back to the super class implementation
                        return super.reduce(jsonList);
                    }
                }
            };
        }

        /**
         * this function checks is the update center tool has the default schema
         * @param jsonList the list of Update centers json files
         * @return true if the schema is the default one (id, name, url), false otherwise
         */
        private boolean isDefaultSchema(List<JSONObject> jsonList) {
            JSONObject jsonToolInstallerList = jsonList.get(0);
            ToolInstallerList toolInstallerList = (ToolInstallerList) JSONObject.toBean(jsonToolInstallerList, ToolInstallerList.class);

            if (toolInstallerList != null) {
                ToolInstallerEntry[] entryList = toolInstallerList.list;
                if (entryList != null) {
                    ToolInstallerEntry sampleEntry = entryList[0];
                    if (sampleEntry != null) {
                        if (sampleEntry.id != null && sampleEntry.name != null && sampleEntry.url != null) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Merge a list of ToolInstallerList and removes duplicate tool installers (ie having the same id)
         * @param jsonList the list of ToolInstallerList to merge
         * @return the merged ToolInstallerList wrapped in a JSONObject
         */
        private JSONObject reduce(List<JSONObject> jsonList) {
            List<ToolInstallerEntry> reducedToolEntries = new ArrayList<>();

            HashSet<String> processedIds = new HashSet<>();
            for (JSONObject jsonToolList : jsonList) {
                ToolInstallerList toolInstallerList = (ToolInstallerList) JSONObject.toBean(jsonToolList, ToolInstallerList.class);
                for (ToolInstallerEntry entry : toolInstallerList.list) {
                    // being able to add the id into the processedIds set means this tool has not been processed before
                    if (processedIds.add(entry.id)) {
                        reducedToolEntries.add(entry);
                    }
                }
            }

            ToolInstallerList toolInstallerList = new ToolInstallerList();
            toolInstallerList.list = new ToolInstallerEntry[reducedToolEntries.size()];
            reducedToolEntries.toArray(toolInstallerList.list);
            //return the list with no duplicates
            return JSONObject.fromObject(toolInstallerList);
        }

        /**
         * This ID needs to be unique, and needs to match the ID token in the JSON update file.
         * <p>
         * By default we use the fully-qualified class name of the {@link DownloadFromUrlInstaller} subtype.
         */
        @Override
        public String getId() {
            return clazz.getName().replace('$', '.');
        }

        /**
         * List of installable tools.
         *
         * <p>
         * The UI uses this information to populate the drop-down. Subtypes can override this method
         * if it wants to change the way the list is filled.
         *
         * @return never null.
         */
        public List<? extends Installable> getInstallables() throws IOException {
            JSONObject d = Downloadable.get(getId()).getData();
            if (d == null)     return Collections.emptyList();
            return Arrays.asList(((InstallableList) JSONObject.toBean(d, InstallableList.class)).list);
        }
    }

    /**
     * Used for JSON databinding to parse the obtained list.
     */
    public static class InstallableList {
        // initialize with an empty array just in case JSON doesn't have the list field (which shouldn't happen.)
        public Installable[] list = new Installable[0];
    }

    /**
     * Downloadable and installable tool.
     */
    public static class Installable {
        /**
         * Used internally to uniquely identify the name.
         */
        public String id;
        /**
         * This is the human readable name.
         */
        public String name;
        /**
         * URL.
         */
        public String url;
    }

    /**
     * Convenient abstract class to implement a NodeSpecificInstallable based on an existing Installable
     * @since 1.626
     */
    public abstract class NodeSpecificInstallable extends Installable implements NodeSpecific<NodeSpecificInstallable> {

        protected NodeSpecificInstallable(Installable inst) {
            this.id = inst.id;
            this.name = inst.name;
            this.url = inst.url;
        }
    }
}
