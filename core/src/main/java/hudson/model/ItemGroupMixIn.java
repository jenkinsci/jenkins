package hudson.model;

import hudson.util.CopyOnWriteMap;
import hudson.util.Function1;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;

/**
 * Defines a bunch of static methods to be used as a "mix-in" for {@link ItemGroup}
 * implementations.
 *
 * @author Kohsuke Kawaguchi
 */
public class ItemGroupMixIn {
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
                V item = (V) Items.load(parent,subdir);
                configurations.put(key.call(item), item);
            } catch (IOException e) {
                e.printStackTrace(); // TODO: logging
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
}
