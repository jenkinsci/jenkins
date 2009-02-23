/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson;

import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.Hudson;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import org.jvnet.tiger_types.Types;

/**
 * {@link ExtensionList} for holding a set of {@link Descriptor}s, which is a group of descriptors for
 * the same extension point.
 *
 * Use {@link Hudson#getDescriptorList(Class)} to obtain instances.
 *
 * @since 1.286
 */
public final class DescriptorExtensionList<T extends Describable<T>> extends ExtensionList<Descriptor<T>> {
    /**
     * Type of the {@link Describable} that this extension list retains.
     */
    private final Class<T> describableType;

    public DescriptorExtensionList(Hudson hudson, Class<T> describableType) {
        super(hudson, (Class)Descriptor.class,allocateLegacyInstanceStore(describableType));
        this.describableType = describableType;
    }

    /**
     * Loading the descriptors in this case means filtering the descriptor from the master {@link ExtensionList}.
     */
    @Override
    protected List<Descriptor<T>> load() {
        List r = new ArrayList();
        for( Descriptor d : hudson.getExtensionList(Descriptor.class) ) {
            Type subTyping = Types.getBaseClass(d.getClass(), Descriptor.class);
            if (!(subTyping instanceof ParameterizedType)) {
                LOGGER.severe(d.getClass()+" doesn't extend Descriptor with a type parameter");
                continue;   // skip this one
            }
            if(Types.getTypeArgument(subTyping,0)==describableType)
                r.add(d);
        }
        return r;
    }

    /**
     * Obtain the statically-scoped storage to store manulaly registered {@link Descriptor}s.
     */
    private static List allocateLegacyInstanceStore(Class describableType) {
        List r = legacyDescriptors.get(describableType);
        if(r!=null)     return r;

        r = new CopyOnWriteArrayList();
        List x = legacyDescriptors.putIfAbsent(describableType, r);
        if(x==null) return r;
        return x;
    }

    /**
     * Stores manually registered Descriptor instances.
     */
    private static final ConcurrentHashMap<Class,List> legacyDescriptors = new ConcurrentHashMap<Class,List>();

    private static final Logger LOGGER = Logger.getLogger(DescriptorExtensionList.class.getName());
}
