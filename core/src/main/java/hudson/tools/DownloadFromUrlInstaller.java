package hudson.tools;

import hudson.FilePath;
import hudson.model.DownloadService.Downloadable;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.net.URL;

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
     * This check needs to run fairly efficiently. The current implementation uses the souce URL of {@link Installable},
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
        for (Installable i : ((DescriptorImpl<?>)getDescriptor()).getInstallables())
            if(id.equals(i.id))
                return i;
        return null;
    }

    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath expected = preferredLocation(tool, node);

        Installable inst = getInstallable();
        if(inst==null) {
            log.getLogger().println("Invalid tool ID "+id);
            return expected;
        }

        if (inst instanceof NodeSpecific) {
            inst = (Installable) ((NodeSpecific) inst).forNode(node, log);
        }

        if(isUpToDate(expected,inst))
            return expected;

        if(expected.installIfNecessaryFrom(new URL(inst.url), log, "Unpacking " + inst.url + " to " + expected + " on " + node.getDisplayName())) {
            expected.child(".timestamp").delete(); // we don't use the timestamp
            FilePath base = findPullUpDirectory(expected);
            if(base!=null && base!=expected)
                base.moveAllChildrenTo(expected);
            // leave a record for the next up-to-date check
            expected.child(".installedFrom").write(inst.url,"UTF-8");
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
     *      http://archive.apache.org/dist/ant/binaries/jakarta-ant-1.1.zip , this directory would contain
     *      a single directory "jakarta-ant".
     *
     * @return
     *      Return the real top directory inside {@code root} that contains the meat. In the above example,
     *      <tt>root.child("jakarta-ant")</tt> should be returned. If there's no directory to pull up,
     *      return null. 
     */
    protected FilePath findPullUpDirectory(FilePath root) throws IOException, InterruptedException {
        // if the directory just contains one directory and that alone, assume that's the pull up subject
        // otherwise leave it as is.
        List<FilePath> children = root.list();
        if(children.size()!=1)    return null;
        if(children.get(0).isDirectory())
            return children.get(0);
        return null;
    }

    public static abstract class DescriptorImpl<T extends DownloadFromUrlInstaller> extends ToolInstallerDescriptor<T> {
        
        @SuppressWarnings("deprecation") // intentionally adding dynamic item here
        protected DescriptorImpl() {
            Downloadable.all().add(createDownloadable());
        }

        /**
         * function that creates a {@link Downloadable}.
         * @return a downloadable object
         */
        public Downloadable createDownloadable() {
            if (this instanceof DownloadFromUrlInstaller.DescriptorImpl) {
                final DownloadFromUrlInstaller.DescriptorImpl delegate = (DownloadFromUrlInstaller.DescriptorImpl)this;
                return new Downloadable(getId()) {
                    public JSONObject reduce(List<JSONObject> jsonList) {
                        return delegate.reduce(jsonList);
                    }
                };
            }
            return new Downloadable(getId());
        }

        private JSONObject reduce(List<JSONObject> jsonList) {
            List<ToolInstallerEntry> reducedToolEntries = new LinkedList<>();
            //collect all tool installers objects from the multiple json objects
            for (JSONObject jsonToolList : jsonList) {
                ToolInstallerList toolInstallerList = (ToolInstallerList) JSONObject.toBean(jsonToolList, ToolInstallerList.class);
                reducedToolEntries.addAll(Arrays.asList(toolInstallerList.list));
            }

            while (Downloadable.hasDuplicates(reducedToolEntries, "id")) {
                List<ToolInstallerEntry> tmpToolInstallerEntries = new LinkedList<>();
                //we need to skip the processed entries
                boolean processed[] = new boolean[reducedToolEntries.size()];
                for (int i = 0; i < reducedToolEntries.size(); i++) {
                    if (processed[i] == true) {
                        continue;
                    }
                    ToolInstallerEntry data1 = reducedToolEntries.get(i);
                    boolean hasDuplicate = false;
                    for (int j = i + 1; j < reducedToolEntries.size(); j ++) {
                        ToolInstallerEntry data2 = reducedToolEntries.get(j);
                        //if we found a duplicate we choose the first one
                        if (data1.id.equals(data2.id)) {
                            hasDuplicate = true;
                            processed[j] = true;
                            tmpToolInstallerEntries.add(data1);
                            //after the first duplicate has been found we break the loop since the duplicates are
                            //processed two by two
                            break;
                        }
                    }
                    //if no duplicate has been found we just insert the entry in the tmp list
                    if (!hasDuplicate) {
                        tmpToolInstallerEntries.add(data1);
                    }
                }
                reducedToolEntries = tmpToolInstallerEntries;
            }

            ToolInstallerList toolInstallerList = new ToolInstallerList();
            toolInstallerList.list = new ToolInstallerEntry[reducedToolEntries.size()];
            reducedToolEntries.toArray(toolInstallerList.list);
            JSONObject reducedToolEntriesJsonList = JSONObject.fromObject(toolInstallerList);
            //return the list with no duplicates
            return reducedToolEntriesJsonList;
        }

        /**
         * This ID needs to be unique, and needs to match the ID token in the JSON update file.
         * <p>
         * By default we use the fully-qualified class name of the {@link DownloadFromUrlInstaller} subtype.
         */
        public String getId() {
            return clazz.getName().replace('$','.');
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
            if(d==null)     return Collections.emptyList();
            return Arrays.asList(((InstallableList)JSONObject.toBean(d,InstallableList.class)).list);
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

        public NodeSpecificInstallable(Installable inst) {
            this.id = inst.id;
            this.name = inst.name;
            this.url = inst.url;
        }
    }
}
