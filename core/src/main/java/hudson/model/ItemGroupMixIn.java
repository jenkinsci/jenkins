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
