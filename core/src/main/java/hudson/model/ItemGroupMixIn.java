/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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

import hudson.Util;
import hudson.XmlFile;
import hudson.model.listeners.ItemListener;
import hudson.security.AccessControlled;
import hudson.util.CopyOnWriteMap;
import hudson.util.Function1;
import jenkins.model.ItemCategory.Categories;
import jenkins.model.ItemCategory.Category;
import jenkins.model.ItemCategory.ItemCategory;
import jenkins.model.ItemCategory.ItemCategoryConfigurator;
import jenkins.model.Jenkins;
import jenkins.util.xml.XMLUtils;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.xml.sax.SAXException;

/**
 * Defines a bunch of static methods to be used as a "mix-in" for {@link ItemGroup}
 * implementations. Not meant for a consumption from outside {@link ItemGroup}s.
 *
 * @author Kohsuke Kawaguchi
 * @see ViewGroupMixIn
 */
public abstract class ItemGroupMixIn {
    /**
     * {@link ItemGroup} for which we are working.
     */
    private final ItemGroup parent;
    private final AccessControlled acl;

    protected ItemGroupMixIn(ItemGroup parent, AccessControlled acl) {
        this.parent = parent;
        this.acl = acl;
    }

    /*
    * Callback methods to be implemented by the ItemGroup implementation.
    */

    /**
     * Adds a newly created item to the parent.
     */
    protected abstract void add(TopLevelItem item);

    /**
     * Assigns the root directory for a prospective item.
     */
    protected abstract File getRootDirFor(String name);


/*
 * The rest is the methods that provide meat.
 */

    /**
     * Loads all the child {@link Item}s.
     *
     * @param modulesDir
     *      Directory that contains sub-directories for each child item.
     */
    public static <K,V extends Item> Map<K,V> loadChildren(ItemGroup parent, File modulesDir, Function1<? extends K,? super V> key) {
        modulesDir.mkdirs(); // make sure it exists

        File[] subdirs = modulesDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        CopyOnWriteMap.Tree<K,V> configurations = new CopyOnWriteMap.Tree<K,V>();
        for (File subdir : subdirs) {
            try {
                // Try to retain the identity of an existing child object if we can.
                V item = (V) parent.getItem(subdir.getName());
                if (item == null) {
                    XmlFile xmlFile = Items.getConfigFile(subdir);
                    if (xmlFile.exists()) {
                        item = (V) Items.load(parent, subdir);
                    } else {
                        Logger.getLogger(ItemGroupMixIn.class.getName()).log(Level.WARNING, "could not find file " + xmlFile.getFile());
                        continue;
                    }
                } else {
                    item.onLoad(parent, subdir.getName());
                }
                configurations.put(key.call(item), item);
            } catch (Exception e) {
                Logger.getLogger(ItemGroupMixIn.class.getName()).log(Level.WARNING, "could not load " + subdir, e);
            }
        }

        return configurations;
    }

    /**
     * {@link Item} -> name function.
     */
    public static final Function1<String,Item> KEYED_BY_NAME = new Function1<String, Item>() {
        public String call(Item item) {
            return item.getName();
        }
    };

    /**
     * Creates a {@link TopLevelItem} from the submission of the '/lib/hudson/newFromList/formList'
     * or throws an exception if it fails.
     */
    public synchronized TopLevelItem createTopLevelItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        acl.checkPermission(Item.CREATE);

        TopLevelItem result;

        String requestContentType = req.getContentType();
        String mode = req.getParameter("mode");
        if (requestContentType == null
                && !(mode != null && mode.equals("copy")))
            throw new Failure("No Content-Type header set");

        boolean isXmlSubmission = requestContentType != null
            && (requestContentType.startsWith("application/xml")
                    || requestContentType.startsWith("text/xml"));

        String name = req.getParameter("name");
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        {// check if the name looks good
            Jenkins.checkGoodName(name);
            name = name.trim();
            if(parent.getItem(name)!=null)
                throw new Failure(Messages.Hudson_JobAlreadyExists(name));
        }

        if(mode!=null && mode.equals("copy")) {
            String from = req.getParameter("from");

            // resolve a name to Item
            Item src = null;
            if (!from.startsWith("/"))
                src = parent.getItem(from);
            if (src==null)
                src = Jenkins.getInstance().getItemByFullName(from);

            if(src==null) {
                if(Util.fixEmpty(from)==null)
                    throw new Failure("Specify which job to copy");
                else
                    throw new Failure("No such job: "+from);
            }
            if (!(src instanceof TopLevelItem))
                throw new Failure(from+" cannot be copied");

            result = copy((TopLevelItem) src,name);
        } else {
            if(isXmlSubmission) {
                result = createProjectFromXML(name, req.getInputStream());
                rsp.setStatus(HttpServletResponse.SC_OK);
                return result;
            } else {
                if(mode==null)
                    throw new Failure("No mode given");
                TopLevelItemDescriptor descriptor = Items.all().findByName(mode);
                if (descriptor == null) {
                    throw new Failure("No item type ‘" + mode + "’ is known");
                }
                descriptor.checkApplicableIn(parent);
                acl.getACL().checkCreatePermission(parent, descriptor);

                // create empty job and redirect to the project config screen
                result = createProject(descriptor, name, true);
            }
        }

        rsp.sendRedirect2(redirectAfterCreateItem(req, result));
        return result;
    }

    /**
     * Computes the redirection target URL for the newly created {@link TopLevelItem}.
     */
    protected String redirectAfterCreateItem(StaplerRequest req, TopLevelItem result) throws IOException {
        return req.getContextPath()+'/'+result.getUrl()+"configure";
    }

    /**
     * Copies an existing {@link TopLevelItem} to a new name.
     */
    @SuppressWarnings({"unchecked"})
    public synchronized <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        acl.checkPermission(Item.CREATE);
        src.checkPermission(Item.EXTENDED_READ);
        src.getDescriptor().checkApplicableIn(parent);
        acl.getACL().checkCreatePermission(parent, src.getDescriptor());

        T result = (T)createProject(src.getDescriptor(),name,false);

        // copy config
        Util.copyFile(Items.getConfigFile(src).getFile(),Items.getConfigFile(result).getFile());

        // reload from the new config
        final File rootDir = result.getRootDir();
        result = Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<T,IOException>() {
            @Override public T call() throws IOException {
                return (T) Items.load(parent, rootDir);
            }
        });
        result.onCopiedFrom(src);

        add(result);
        ItemListener.fireOnCopied(src,result);
        Jenkins.getInstance().rebuildDependencyGraphAsync();

        return result;
    }

    public synchronized TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        acl.checkPermission(Item.CREATE);

        Jenkins.getInstance().getProjectNamingStrategy().checkName(name);
        if (parent.getItem(name) != null) {
            throw new IllegalArgumentException(parent.getDisplayName() + " already contains an item '" + name + "'");
        }
        // TODO what if we have no DISCOVER permission on the existing job?

        // place it as config.xml
        File configXml = Items.getConfigFile(getRootDirFor(name)).getFile();
        final File dir = configXml.getParentFile();
        dir.mkdirs();
        boolean success = false;
        try {
            XMLUtils.safeTransform((Source)new StreamSource(xml), new StreamResult(configXml));

            // load it
            TopLevelItem result = Items.whileUpdatingByXml(new NotReallyRoleSensitiveCallable<TopLevelItem,IOException>() {
                @Override public TopLevelItem call() throws IOException {
                    return (TopLevelItem) Items.load(parent, dir);
                }
            });

            success = acl.getACL().hasCreatePermission(Jenkins.getAuthentication(), parent, result.getDescriptor())
                && result.getDescriptor().isApplicableIn(parent);

            add(result);

            ItemListener.fireOnCreated(result);
            Jenkins.getInstance().rebuildDependencyGraphAsync();

            return result;
        } catch (TransformerException e) {
            success = false;
            throw new IOException("Failed to persist config.xml", e);
        } catch (SAXException e) {
            success = false;
            throw new IOException("Failed to persist config.xml", e);
        } catch (IOException e) {
            success = false;
            throw e;
        } catch (RuntimeException e) {
            success = false;
            throw e;
        } finally {
            if (!success) {
                // if anything fails, delete the config file to avoid further confusion
                Util.deleteRecursive(dir);
            }
        }
    }

    public synchronized TopLevelItem createProject( TopLevelItemDescriptor type, String name, boolean notify )
            throws IOException {
        acl.checkPermission(Item.CREATE);
        type.checkApplicableIn(parent);
        acl.getACL().checkCreatePermission(parent, type);

        Jenkins.getInstance().getProjectNamingStrategy().checkName(name);
        if(parent.getItem(name)!=null)
            throw new IllegalArgumentException("Project of the name "+name+" already exists");
        // TODO problem with DISCOVER as noted above

        TopLevelItem item = type.newInstance(parent, name);
        try {
            callOnCreatedFromScratch(item);
        } catch (AbstractMethodError e) {
            // ignore this error. Must be older plugin that doesn't have this method
        }
        item.save();
        add(item);
        Jenkins.getInstance().rebuildDependencyGraphAsync();

        if (notify)
            ItemListener.fireOnCreated(item);

        return item;
    }

    /**
     * Populate a {$link Categories} from a specific {$link ItemGroup}.
     *
     * @return
     */
    public static Categories getCategories(Authentication a, ItemGroup c) {
        Categories categories = new Categories();
        for (TopLevelItemDescriptor descriptor : Items.all(a, c)) {
            ItemCategory ic = ItemCategoryConfigurator.getCategory(descriptor);
            int i = 0;
            boolean found = false;
            while (i < categories.getItems().size() && !found) {
                if (categories.getItems().get(i).getId().equals(ic.getId())) {
                    Map<String, Object> metadata = new HashMap<String, Object>();
                    metadata.put("class", descriptor.clazz.getName());
                    metadata.put("iconClassName", "item-icon-" + descriptor.clazz.getName().substring(descriptor.clazz.getName().lastIndexOf(".") + 1).toLowerCase());
                    metadata.put("weight", ItemCategoryConfigurator.getWeight(descriptor));
                    categories.getItems().get(i).getItems().add(metadata);
                    found = true;
                }
                i++;
            }
            if (!found) {
                Map<String, Object> metadata = new HashMap<String, Object>();
                metadata.put("class", descriptor.clazz.getName());
                metadata.put("iconClassName", "item-icon-" + descriptor.clazz.getName().substring(descriptor.clazz.getName().lastIndexOf(".") + 1).toLowerCase());
                metadata.put("weight", ItemCategoryConfigurator.getWeight(descriptor));
                List<Map<String, Object>> temp = new ArrayList<Map<String, Object>>();
                temp.add(metadata);
                categories.getItems().add(new Category(ic.getId(), ic.getDisplayName(), ic.getDescription(),
                        ic.getIconClassName(), ic.getWeight(), temp));
            }
        }
        return categories;
    }

    /**
     * Pointless wrapper to avoid HotSpot problem. See JENKINS-5756
     */
    private void callOnCreatedFromScratch(TopLevelItem item) {
        item.onCreatedFromScratch();
    }
}
