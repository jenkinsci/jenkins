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
import hudson.XmlFile;
import hudson.model.listeners.ItemListener;
import hudson.remoting.Callable;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.triggers.Trigger;
import hudson.util.DescriptorList;
import hudson.util.EditDistance;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

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
    @Deprecated
    public static final List<TopLevelItemDescriptor> LIST = (List)new DescriptorList<TopLevelItem>(TopLevelItem.class);

    /**
     * Used to behave differently when loading posted configuration as opposed to persisted configuration.
     * @see Trigger#start
     * @since 1.482
     */
    private static final ThreadLocal<Boolean> updatingByXml = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
    };

    /**
     * Runs a block while making {@link #currentlyUpdatingByXml} be temporarily true.
     * Use this when you are creating or changing an item.
     * @param <V> a return value type (may be {@link Void})
     * @param <T> an error type (may be {@link Error})
     * @param callable a block, typically running {@link #load} or {@link Item#onLoad}
     * @return whatever {@code callable} returned
     * @throws T anything {@code callable} throws
     * @since 1.546
     */
    public static <V,T extends Throwable> V whileUpdatingByXml(Callable<V,T> callable) throws T {
        updatingByXml.set(true);
        try {
            return callable.call();
        } finally {
            updatingByXml.set(false);
        }
    }

    /**
     * Checks whether we are in the middle of creating or configuring an item via XML.
     * Used to determine the {@code newInstance} parameter for {@link Trigger#start}.
     * @return true if {@link #whileUpdatingByXml} is currently being called, false for example when merely starting Jenkins or reloading from disk
     * @since 1.546
     */
    public static boolean currentlyUpdatingByXml() {
        return updatingByXml.get();
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s.
     */
    public static DescriptorExtensionList<TopLevelItem,TopLevelItemDescriptor> all() {
        return Jenkins.getInstance().<TopLevelItem,TopLevelItemDescriptor>getDescriptorList(TopLevelItem.class);
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s that the current security principal is allowed to
     * create within the specified item group.
     *
     * @since 1.607
     */
    public static List<TopLevelItemDescriptor> all(ItemGroup c) {
        return all(Jenkins.getAuthentication(), c);
    }

    /**
     * Returns all the registered {@link TopLevelItemDescriptor}s that the specified security principal is allowed to
     * create within the specified item group.
     *
     * @since 1.607
     */
    public static List<TopLevelItemDescriptor> all(Authentication a, ItemGroup c) {
        List<TopLevelItemDescriptor> result = new ArrayList<TopLevelItemDescriptor>();
        ACL acl;
        if (c instanceof AccessControlled) {
            acl = ((AccessControlled) c).getACL();
        } else {
            // fall back to root
            acl = Jenkins.getInstance().getACL();
        }
        for (TopLevelItemDescriptor d: all()) {
            if (acl.hasCreatePermission(a, c, d) && d.isApplicableIn(c)) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * @deprecated Underspecified what the parameter is. {@link Descriptor#getId}? A {@link Describable} class name?
     */
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
    @Deprecated
    public static <T extends Item> List<T> fromNameList(String list, Class<T> type) {
        return fromNameList(null,list,type);
    }

    /**
     * Does the opposite of {@link #toNameList(Collection)}.
     */
    public static <T extends Item> List<T> fromNameList(ItemGroup context, @Nonnull String list, @Nonnull Class<T> type) {
        final Jenkins jenkins = Jenkins.getInstance();
        
        List<T> r = new ArrayList<T>();
        if (jenkins == null) {
            return r;
        }
        
        StringTokenizer tokens = new StringTokenizer(list,",");
        while(tokens.hasMoreTokens()) {
            String fullName = tokens.nextToken().trim();
            if (StringUtils.isNotEmpty(fullName)) {
                T item = jenkins.getItem(fullName, context, type);
                if(item!=null)
                    r.add(item);
            }
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

        Stack<String> name = new Stack<String>();
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
                if (name.size() == 0) {
                    throw new IllegalArgumentException(String.format(
                            "Illegal relative path '%s' within context '%s'", path, context.getFullName()
                    ));
                }
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
    static String getRelativeNameFrom(String itemFullName, String groupFullName) {
        String[] itemFullNameA = itemFullName.isEmpty() ? new String[0] : itemFullName.split("/");
        String[] groupFullNameA = groupFullName.isEmpty() ? new String[0] : groupFullName.split("/");
        for (int i = 0; ; i++) {
            if (i == itemFullNameA.length) {
                if (i == groupFullNameA.length) {
                    // itemFullName and groupFullName are identical
                    return ".";
                } else {
                    // itemFullName is an ancestor of groupFullName; insert ../ for rest of groupFullName
                    StringBuilder b = new StringBuilder();
                    for (int j = 0; j < groupFullNameA.length - itemFullNameA.length; j++) {
                        if (j > 0) {
                            b.append('/');
                        }
                        b.append("..");
                    }
                    return b.toString();
                }
            } else if (i == groupFullNameA.length) {
                // groupFullName is an ancestor of itemFullName; insert rest of itemFullName
                StringBuilder b = new StringBuilder();
                for (int j = i; j < itemFullNameA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append(itemFullNameA[j]);
                }
                return b.toString();
            } else if (itemFullNameA[i].equals(groupFullNameA[i])) {
                // identical up to this point
                continue;
            } else {
                // first mismatch; insert ../ for rest of groupFullName, then rest of itemFullName
                StringBuilder b = new StringBuilder();
                for (int j = i; j < groupFullNameA.length; j++) {
                    if (j > i) {
                        b.append('/');
                    }
                    b.append("..");
                }
                for (int j = i; j < itemFullNameA.length; j++) {
                    b.append('/').append(itemFullNameA[j]);
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
        getAllItems(root, type, r);
        return r;
    }
    private static <T extends Item> void getAllItems(final ItemGroup root, Class<T> type, List<T> r) {
        List<Item> items = new ArrayList<Item>(((ItemGroup<?>) root).getItems());
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item i1, Item i2) {
                return name(i1).compareToIgnoreCase(name(i2));
            }
            String name(Item i) {
                String n = i.getName();
                if (i instanceof ItemGroup) {
                    n += '/';
                }
                return n;
            }
        });
        for (Item i : items) {
            if (type.isInstance(i)) {
                if (i.hasPermission(Item.READ)) {
                    r.add(type.cast(i));
                }
            }
            if (i instanceof ItemGroup) {
                getAllItems((ItemGroup) i, type, r);
            }
        }
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
     * @since 1.548
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
        item.movedTo(destination, newItem, destDir);
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
    }
}
