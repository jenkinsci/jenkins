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
package hudson.model.listeners;

import com.google.common.base.Function;
import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.security.ACL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.NotReallyRoleSensitiveCallable;

/**
 * Receives notifications about CRUD operations of {@link Item}.
 *
 * @since 1.74
 * @author Kohsuke Kawaguchi
 */
public class ItemListener implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(ItemListener.class.getName());

    /**
     * Called after a new job is created and added to {@link jenkins.model.Jenkins},
     * before the initial configuration page is provided.
     * <p>
     * This is useful for changing the default initial configuration of newly created jobs.
     * For example, you can enable/add builders, etc.
     */
    public void onCreated(Item item) {
    }

    /**
     * Called after a new job is created by copying from an existing job.
     *
     * For backward compatibility, the default implementation of this method calls {@link #onCreated(Item)}.
     * If you choose to handle this method, think about whether you want to call super.onCopied or not.
     *
     *
     * @param src
     *      The source item that the new one was copied from. Never null.
     * @param  item
     *      The newly created item. Never null.
     *
     * @since 1.325
     *      Before this version, a copy triggered {@link #onCreated(Item)}.
     */
    public void onCopied(Item src, Item item) {
        onCreated(item);
    }

    /**
     * Called after all the jobs are loaded from disk into {@link jenkins.model.Jenkins}
     * object.
     */
    public void onLoaded() {
    }

    /**
     * Called right before a job is going to be deleted.
     *
     * At this point the data files of the job is already gone.
     */
    public void onDeleted(Item item) {
    }

    /**
     * Called after a job is renamed.
     * Most implementers should rather use {@link #onLocationChanged}.
     * @param item
     *      The job being renamed.
     * @param oldName
     *      The old name of the job.
     * @param newName
     *      The new name of the job. Same as {@link Item#getName()}.
     * @since 1.146
     */
    public void onRenamed(Item item, String oldName, String newName) {
    }

    /**
     * Called after an item’s fully-qualified location has changed.
     * This might be because:
     * <ul>
     * <li>This item was renamed.
     * <li>Some ancestor folder was renamed.
     * <li>This item was moved between folders (or from a folder to Jenkins root or vice-versa).
     * <li>Some ancestor folder was moved.
     * </ul>
     * Where applicable, {@link #onRenamed} will already have been called on this item or an ancestor.
     * And where applicable, {@link #onLocationChanged} will already have been called on its ancestors.
     * <p>This method should be used (instead of {@link #onRenamed}) by any code
     * which seeks to keep (absolute) references to items up to date:
     * if a persisted reference matches {@code oldFullName}, replace it with {@code newFullName}.
     * @param item an item whose absolute position is now different
     * @param oldFullName the former {@link Item#getFullName}
     * @param newFullName the current {@link Item#getFullName}
     * @see Items#computeRelativeNamesAfterRenaming
     * @since 1.548
     */
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {}

    /**
     * Called after a job has its configuration updated.
     *
     * @since 1.460
     */
    public void onUpdated(Item item) {
    }

    /**
     * @since 1.446
     *      Called at the begenning of the orderly shutdown sequence to
     *      allow plugins to clean up stuff
     */
    public void onBeforeShutdown() {
    }

    /**
     * Registers this instance to Hudson and start getting notifications.
     *
     * @deprecated as of 1.286
     *      put {@link Extension} on your class to have it auto-registered.
     */
    @Deprecated
    public void register() {
        all().add(this);
    }

    /**
     * All the registered {@link ItemListener}s.
     */
    public static ExtensionList<ItemListener> all() {
        return ExtensionList.lookup(ItemListener.class);
    }

    // TODO JENKINS-21224 generalize this to a method perhaps in ExtensionList and use consistently from all listeners
    private static void forAll(final /* java.util.function.Consumer<ItemListener> */Function<ItemListener,Void> consumer) {
        for (ItemListener l : all()) {
            try {
                consumer.apply(l);
            } catch (RuntimeException x) {
                LOGGER.log(Level.WARNING, "failed to send event to listener of " + l.getClass(), x);
            }
        }
    }

    public static void fireOnCopied(final Item src, final Item result) {
        forAll(new Function<ItemListener,Void>() {
            @Override public Void apply(ItemListener l) {
                l.onCopied(src, result);
                return null;
            }
        });
    }

    public static void fireOnCreated(final Item item) {
        forAll(new Function<ItemListener,Void>() {
            @Override public Void apply(ItemListener l) {
                l.onCreated(item);
                return null;
            }
        });
    }

    public static void fireOnUpdated(final Item item) {
        forAll(new Function<ItemListener,Void>() {
            @Override public Void apply(ItemListener l) {
                l.onUpdated(item);
                return null;
            }
        });
    }

    /** @since 1.548 */
    public static void fireOnDeleted(final Item item) {
        forAll(new Function<ItemListener,Void>() {
            @Override public Void apply(ItemListener l) {
                l.onDeleted(item);
                return null;
            }
        });
    }

    /**
     * Calls {@link #onRenamed} and {@link #onLocationChanged} as appropriate.
     * @param rootItem the topmost item whose location has just changed
     * @param oldFullName the previous {@link Item#getFullName}
     * @since 1.548
     */
    public static void fireLocationChange(final Item rootItem, final String oldFullName) {
        String prefix = rootItem.getParent().getFullName();
        if (!prefix.isEmpty()) {
            prefix += '/';
        }
        final String newFullName = rootItem.getFullName();
        assert newFullName.startsWith(prefix);
        int prefixS = prefix.length();
        if (oldFullName.startsWith(prefix) && oldFullName.indexOf('/', prefixS) == -1) {
            final String oldName = oldFullName.substring(prefixS);
            final String newName = rootItem.getName();
            assert newName.equals(newFullName.substring(prefixS));
            forAll(new Function<ItemListener, Void>() {
                @Override public Void apply(ItemListener l) {
                    l.onRenamed(rootItem, oldName, newName);
                    return null;
                }
            });
        }
        forAll(new Function<ItemListener, Void>() {
            @Override public Void apply(ItemListener l) {
                l.onLocationChanged(rootItem, oldFullName, newFullName);
                return null;
            }
        });
        if (rootItem instanceof ItemGroup) {
            for (final Item child : ACL.impersonate(ACL.SYSTEM, new NotReallyRoleSensitiveCallable<List<Item>,RuntimeException>() {
                @Override public List<Item> call() {
                    return Items.getAllItems((ItemGroup) rootItem, Item.class);
                }
            })) {
                final String childNew = child.getFullName();
                assert childNew.startsWith(newFullName);
                assert childNew.charAt(newFullName.length()) == '/';
                final String childOld = oldFullName + childNew.substring(newFullName.length());
                forAll(new Function<ItemListener, Void>() {
                    @Override public Void apply(ItemListener l) {
                        l.onLocationChanged(child, childOld, childNew);
                        return null;
                    }
                });
            }
        }
    }

}
