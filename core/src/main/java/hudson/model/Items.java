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
import hudson.triggers.Trigger;
import hudson.util.DescriptorList;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
     * Compute the relative name of list of items after a rename occurred. Used to manage job references as names in
     * plugins to support {@link hudson.model.listeners.ItemListener#onRenamed(hudson.model.Item, String, String)}.
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
                // relative name points to the renamed item, let's compute the new relative name
                newValue.add( computeRelativeNameAfterRenaming(canonicalName, newCanonicalName, relativeName) );
            } else {
                newValue.add(relativeName);
            }
        }
        return StringUtils.join(newValue, ",");
    }

    /**
     * Compute the relative name of an Item after renaming
     */
    private static String computeRelativeNameAfterRenaming(String oldFullName, String newFullName, String relativeName) {

        String[] a = oldFullName.split("/");
        String[] n = newFullName.split("/");
        assert a.length == n.length;
        String[] r = relativeName.split("/");

        int j = a.length-1;
        for(int i=r.length-1;i>=0;i--) {
            String part = r[i];
            if (part.equals("") && i==0) {
                continue;
            }
            if (part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                j--;
                continue;
            }
            if (part.equals(a[j])) {
                r[i] = n[j];
                j--;
                continue;
            }
        }
        return StringUtils.join(r, '/');
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
