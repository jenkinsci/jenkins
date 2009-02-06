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
package hudson.util;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * List of {@link Descriptor}s.
 *
 * <p>
 * This class is really just a list but also defines
 * some Hudson specific methods that operate on
 * {@link Descriptor} list.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.161
 */
public final class DescriptorList<T extends Describable<T>> extends CopyOnWriteArrayList<Descriptor<T>> {
    public DescriptorList(Descriptor<T>... descriptors) {
        super(descriptors);
    }

    /**
     * Creates a new instance of a {@link Describable}
     * from the structured form submission data posted
     * by a radio button group. 
     */
    public T newInstanceFromRadioList(JSONObject config) throws FormException {
        if(config.isNullObject())
            return null;    // none was selected
        int idx = config.getInt("value");
        return get(idx).newInstance(Stapler.getCurrentRequest(),config);
    }

    public T newInstanceFromRadioList(JSONObject parent, String name) throws FormException {
        return newInstanceFromRadioList(parent.getJSONObject(name));
    }

    /**
     * Finds a descriptor by their {@link Descriptor#clazz}.
     *
     * If none is found, null is returned.
     */
    public Descriptor<T> findByName(String fullyQualifiedClassName) {
        for (Descriptor<T> d : this)
            if(d.clazz.getName().equals(fullyQualifiedClassName))
                return d;
        return null;
    }

    /**
     * No-op method used to force the class initialization of the given class.
     * The class initialization in turn is expected to put the descriptor
     * into the {@link DescriptorList}.
     *
     * <p>
     * This is necessary to resolve the class initialization order problem.
     * Often a {@link DescriptorList} is defined in the base class, and
     * when it tries to initialize itself by listing up descriptors of known
     * sub-classes, they might not be available in time.
     *
     * @since 1.162
     */
    public void load(Class<? extends Describable> c) {
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);  // Can't happen
        }
    }

    /**
     * Finds the descriptor that has the matching fully-qualified class name.
     */
    public Descriptor<T> find(String fqcn) {
        return Descriptor.find(this,fqcn);
    }
}
