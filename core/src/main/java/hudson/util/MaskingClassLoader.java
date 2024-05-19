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

package hudson.util;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link ClassLoader} that masks a specified set of classes
 * from its parent class loader.
 *
 * <p>
 * This code is used to create an isolated environment.
 *
 * @author Kohsuke Kawaguchi
 */
public class MaskingClassLoader extends ClassLoader {
    /**
     * Prefix of the packages that should be hidden.
     */
    private final List<String> masksClasses;

    private final List<String> masksResources;

    static {
        registerAsParallelCapable();
    }

    public MaskingClassLoader(ClassLoader parent, String... masks) {
        this(parent, Arrays.asList(masks));
    }

    public MaskingClassLoader(ClassLoader parent, Collection<String> masks) {
        super("Masking ClassLoader of " + parent.getName(), parent);
        this.masksClasses = List.copyOf(masks);

        /*
         * The name of a resource is a '/'-separated path name
         */
        this.masksResources =
                masks.stream().map(mask -> mask.replace('.', '/')).collect(Collectors.toUnmodifiableList());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (String mask : masksClasses) {
            if (name.startsWith(mask))
                throw new ClassNotFoundException();
        }

        return super.loadClass(name, resolve);
    }

    @Override
    public URL getResource(String name) {
        if (isMasked(name)) return null;

        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (isMasked(name)) return Collections.emptyEnumeration();

        return super.getResources(name);
    }

    private boolean isMasked(String name) {
        for (String mask : masksResources) {
            if (name.startsWith(mask))
                return true;
        }
        return false;
    }
}
