/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import com.thoughtworks.xstream.XStream;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixConfiguration;
import hudson.XmlFile;
import hudson.matrix.Axis;
import hudson.model.listeners.ItemListener;
import hudson.triggers.Trigger;
import hudson.util.DescriptorList;
import hudson.util.EditDistance;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.annotation.CheckForNull;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import org.apache.commons.io.FileUtils;

/**
 * Convenience methods related to {@link Item}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Items {
    /**
     * List of all installed {@link TopLevelItem} types.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access and {@link Extension} for registration.
     */
    public static final List<TopLevelItemDescriptor> LIST = (List)new DescriptorList<TopLevelItem>(TopLevelItem.class);

    /**
     * Used to behave differently when loading posted configuration as opposed to persisted configuration.
     * @see Trigger#start
     * @since 1.482
     */
    static final ThreadLocal<Boolean> updatingByXml = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
    };

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s.
     */
    public static DescriptorExtensionList<TopLevelItem,TopLevelItemDescriptor> all() {
        return Jenkins.getInstance().<TopLevelItem,TopLevelItemDescriptor>getDescriptorList(TopLevelItem.class);
    }

    public static TopLevelItemDescriptor getDescriptor(String fqcn) {
        return Descriptor.find(all(), fqcn);
    }

    /**
     * Converts a list of items into a comma-separated list of full names.
     */
    public static String toNameList(Collection<? extends Item> items) {
        StringBuilder buf = new StringBuilder();
        for (Item item : items) {
            if(buf.length()>0)
                buf.append(", ");
            buf.append(item.getFullName());
        }
        return buf.toString();
    }

    /**
     * @deprecated as of 1.406
     *      Use {@link #fromNameList(ItemGroup, String, Class)}
     */
    public static <T extends Item> List<T> fromNameList(String list, Class<T> type) {
        return fromNameList(null,list,type);
    }

    /**
     * Does the opposite of {@link #toNameList(Collection)}.
     */
    public static <T extends Item> List<T> fromNameList(ItemGroup context, String list, Class<T> type) {
        Jenkins hudson = Jenkins.getInstance();

        List<T> r = new ArrayList<T>();
        StringTokenizer tokens = new StringTokenizer(list,",");
        while(tokens.hasMoreTokens()) {
            String fullName = tokens.nextToken().trim();
            T item = hudson.getItem(fullName, context, type);
            if(item!=null)
                r.add(item);
        }
        return r;
    }

    /**
     * Computes the canonical full name of a relative path in an {@link ItemGroup} context, handling relative
     * positions ".." and "." as absolute path starting with "/". The resulting name is the item fullName from Jenkins
     * root.
     */
    public static String getCanonicalName(ItemGroup context, String path) {
        String[] c = context.getFullName().split("/");
        String[] p = path.split("/");

        Stack name = new Stack();
        for (int i=0; i<c.length;i++) {
            if (i==0 && c[i].equals("")) continue;
            name.push(c[i]);
        }
        for (int i=0; i<p.length;i++) {
            if (i==0 && p[i].equals("")) {
                // Absolute path starting with a "/"
                name.clear();
                continue;
            }
            if (p[i].equals("..")) {
                name.pop();
                continue;
            }
            if (p[i].equals(".")) {
                continue;
            }
            name.push(p[i]);
        }
        return StringUtils.join(name, '/');
    }

    /**
     * Computes the relative name of list of items after a rename or move occurred.
     * Used to manage job references as names in plugins to support {@link hudson.model.listeners.ItemListener#onLocationChanged}.
     * <p>
     * In a hierarchical context, when a plugin has a reference to a job as <code>../foo/bar</code> this method will
     * handle the relative path as "foo" is renamed to "zot" to compute <code>../zot/bar</code>
     *
     * @param oldFullName the old full name of the item
     * @param newFullName the new full name of the item
     * @param relativeNames coma separated list of Item relative names
     * @param context the {link ItemGroup} relative names refer to
     * @return relative name for the renamed item, based on the same ItemGroup context
     */
    public static String computeRelativeNamesAfterRenaming(String oldFullName, String newFullName, String relativeNames, ItemGroup context) {

        StringTokenizer tokens = new StringTokenizer(relativeNames,",");
        List<String> newValue = new ArrayList<String>();
        while(tokens.hasMoreTokens()) {
            String relativeName = tokens.nextToken().trim();
            String canonicalName = getCanonicalName(context, relativeName);
            if (canonicalName.equals(oldFullName) || canonicalName.startsWith(oldFullName+'/')) {
                String newCanonicalName = newFullName + canonicalName.substring(oldFullName.length());
                if (relativeName.startsWith("/")) {
                    newValue.add("/" + newCanonicalName);
                } else {
                    newValue.add(getRelativeNameFrom(newCanonicalName, context.getFullName()));
                }
            } else {
                newValue.add(relativeName);
            }
        }
        return StringUtils.join(newValue, ",");
    }

    // Had difficulty adapting the version in Functions to use no live items, so rewrote it:
    static String getRelativeNameFrom(String p, String g) {
        String[] pA = p.isEmpty() ? new String[0] : p.split("/");
        String[] gA = g.isEmpty() ? new String[0] : g.split("/");
        for (int i = 0; ; i++) {
            if (i == pA.length) {
                if (i == gA.length) {
                    // p and g are identical
                    return ".";
                } else {
                    // p is an ancestor of g; insert ../ for rest of g
                    StringBuilder b = new StringBuilder();
                    for (int j = 0; j < gA.length - pA.length; j++) {
                        if (j > 0) {
                            b.append('/');
                        }
                        b.append("..");
                    }
                    return b.toString();
                }
            } else if (i == gA.length) {
                // g is an ancestor of p; insert rest of p
                StringBuilder b = new StringBuilder();
                for (int j = i; j < pA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append(pA[j]);
                }
                return b.toString();
            } else if (pA[i].equals(gA[i])) {
                // identical up to this point
                continue;
            } else {
                // first mismatch; insert ../ for rest of g, then rest of p
                StringBuilder b = new StringBuilder();
                for (int j = i; j < gA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append("..");
                }
                for (int j = i; j < pA.length; j++) {
                    b.append('/').append(pA[j]);
                }
                return b.toString();
            }
        }
    }

    /**
     * Loads a {@link Item} from a config file.
     *
     * @param dir
     *      The directory that contains the config file, not the config file itself.
     */
    public static Item load(ItemGroup parent, File dir) throws IOException {
        Item item = (Item)getConfigFile(dir).read();
        item.onLoad(parent,dir.getName());
        return item;
    }

    /**
     * The file we save our configuration.
     */
    public static XmlFile getConfigFile(File dir) {
        return new XmlFile(XSTREAM,new File(dir,"config.xml"));
    }

    /**
     * The file we save our configuration.
     */
    public static XmlFile getConfigFile(Item item) {
        return getConfigFile(item.getRootDir());
    }
    
    /**
     * Gets all the {@link Item}s recursively in the {@link ItemGroup} tree
     * and filter them by the given type.
     * 
     * @since 1.512
     */
    public static <T extends Item> List<T> getAllItems(final ItemGroup root, Class<T> type) {
        List<T> r = new ArrayList<T>();

        Stack<ItemGroup> q = new Stack<ItemGroup>();
        q.push(root);

        while(!q.isEmpty()) {
            ItemGroup<?> parent = q.pop();
            for (Item i : parent.getItems()) {
                if(type.isInstance(i)) {
                    if (i.hasPermission(Item.READ))
                        r.add(type.cast(i));
                }
                if(i instanceof ItemGroup)
                    q.push((ItemGroup)i);
            }
        }
        // sort by relative name, ignoring case
        Collections.sort(r, new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                if (o1 == null) {
                    if (o2 == null) {
                        return 0;
                    }
                    return 1;
                }
                if (o2 == null) {
                    return -1;
                }
                return o1.getRelativeNameFrom(root).compareToIgnoreCase(o2.getRelativeNameFrom(root));
            }
            
        });
        return r;
    }

    /**
     * Finds an item whose name (when referenced from the specified context) is closest to the given name.
     * @param <T> the type of item being considered
     * @param type same as {@code T}
     * @param name the supplied name
     * @param context a context to start from (used to compute relative names)
     * @return the closest available item
     * @since 1.538
     */
    public static @CheckForNull <T extends Item> T findNearest(Class<T> type, String name, ItemGroup context) {
        List<T> projects = Jenkins.getInstance().getAllItems(type);
        String[] names = new String[projects.size()];
        for (int i = 0; i < projects.size(); i++) {
            names[i] = projects.get(i).getRelativeNameFrom(context);
        }
        String nearest = EditDistance.findNearest(name, names);
        return Jenkins.getInstance().getItem(nearest, context, type);
    }

    /**
     * Moves an item between folders (or top level).
     * Fires all relevant events but does not verify that the item’s directory is not currently being used in some way (for example by a running build).
     * Does not check any permissions.
     * @param item some item (job or folder)
     * @param destination the destination of the move (a folder or {@link Jenkins}); not the current parent (or you could just call {@link AbstractItem#renameTo})
     * @return the new item (usually the same object as {@code item})
     * @throws IOException if the move fails, or some subsequent step fails (directory might have already been moved)
     * @throws IllegalArgumentException if the move would really be a rename, or the destination cannot accept the item, or the destination already has an item of that name
     * @since TODO
     */
    public static <I extends AbstractItem & TopLevelItem> I move(I item, DirectlyModifiableTopLevelItemGroup destination) throws IOException, IllegalArgumentException {
        DirectlyModifiableTopLevelItemGroup oldParent = (DirectlyModifiableTopLevelItemGroup) item.getParent();
        if (oldParent == destination) {
            throw new IllegalArgumentException();
        }
        // TODO verify that destination is to not equal to, or inside, item
        if (!destination.canAdd(item)) {
            throw new IllegalArgumentException();
        }
        String name = item.getName();
        if (destination.getItem(name) != null) {
            throw new IllegalArgumentException(name + " already exists");
        }
        String oldFullName = item.getFullName();
        // TODO AbstractItem.renameTo has a more baroque implementation; factor it out into a utility method perhaps?
        File destDir = destination.getRootDirFor(item);
        FileUtils.forceMkdir(destDir.getParentFile());
        FileUtils.moveDirectory(item.getRootDir(), destDir);
        oldParent.remove(item);
        I newItem = destination.add(item, name);
        newItem.onLoad(destination, name);
        ItemListener.fireLocationChange(newItem, oldFullName);
        return newItem;
    }

    /**
     * Used to load/save job configuration.
     *
     * When you extend {@link Job} in a plugin, try to put the alias so
     * that it produces a reasonable XML.
     */
    public static final XStream XSTREAM = new XStream2();

    /**
     * Alias to {@link #XSTREAM} so that one can access additional methods on {@link XStream2} more easily.
     */
    public static final XStream2 XSTREAM2 = (XStream2)XSTREAM;

    static {
        XSTREAM.alias("project",FreeStyleProject.class);
        XSTREAM.alias("matrix-project",MatrixProject.class);
        XSTREAM.alias("axis", Axis.class);
        XSTREAM.alias("matrix-config",MatrixConfiguration.class);
    }
}
