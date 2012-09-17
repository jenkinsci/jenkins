/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Type-safe instance map.
 *
 * @author Kohsuke Kawaguchi
 */
public class Lookup {
    private final ConcurrentHashMap<Class,Object> data = new ConcurrentHashMap<Class,Object>();

    public <T> T get(Class<T> type) {
        return type.cast(data.get(type));
    }

    public <T> T set(Class<T> type, T instance) {
        return type.cast(data.put(type,instance));
    }

    /**
     * Overwrites the value only if the current value is null.
     *
     * @return
     *      If the value was null, return the {@code instance} value.
     *      Otherwise return the current value, which is non-null.
     */
    public <T> T setIfNull(Class<T> type, T instance) {
        Object o = data.putIfAbsent(type, instance);
        if (o!=null)    return type.cast(o);
        return instance;
    }
}
