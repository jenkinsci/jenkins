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
package hudson.remoting;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manages unique ID for classloaders.
 *
 * @author Kohsuke Kawaguchi
 */
final class ExportedClassLoaderTable {
    private final Map<Integer, WeakReference<ClassLoader>> table = new HashMap<Integer, WeakReference<ClassLoader>>();
    private final WeakHashMap<ClassLoader,Integer> reverse = new WeakHashMap<ClassLoader,Integer>();

    // id==0 is reserved for bootstrap classloader
    private int iota = 1;


    public synchronized int intern(ClassLoader cl) {
        if(cl==null)    return 0;   // bootstrap classloader

        Integer id = reverse.get(cl);
        if(id==null) {
            id = iota++;
            table.put(id,new WeakReference<ClassLoader>(cl));
            reverse.put(cl,id);
        }

        return id;
    }

    public synchronized ClassLoader get(int id) {
        WeakReference<ClassLoader> ref = table.get(id);
        if(ref==null)   return null;
        return ref.get();
    }
}
