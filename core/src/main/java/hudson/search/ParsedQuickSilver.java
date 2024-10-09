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

package hudson.search;

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed {@link QuickSilver}s so that {@link SearchIndex} can be easily created.
 * One instance per one class.
 *
 * @author Kohsuke Kawaguchi
 */
final class ParsedQuickSilver {
    private static final Map<Class, ParsedQuickSilver> TABLE = new HashMap<>();

    static synchronized ParsedQuickSilver get(Class<? extends SearchableModelObject> clazz) {
        ParsedQuickSilver pqs = TABLE.get(clazz);
        if (pqs == null)
            TABLE.put(clazz, pqs = new ParsedQuickSilver(clazz));
        return pqs;
    }

    private final List<Getter> getters = new ArrayList<>();

    private ParsedQuickSilver(Class<? extends SearchableModelObject> clazz) {
        QuickSilver qs;

        for (Method m : clazz.getMethods()) {
            qs = m.getAnnotation(QuickSilver.class);
            if (qs != null) {
                String url = stripGetPrefix(m);
                if (qs.value().length == 0)
                    getters.add(new MethodGetter(url, splitName(url), m));
                else {
                    for (String name : qs.value())
                        getters.add(new MethodGetter(url, name, m));
                }
            }
        }
        for (Field f : clazz.getFields()) {
            qs = f.getAnnotation(QuickSilver.class);
            if (qs != null) {
                if (qs.value().length == 0)
                    getters.add(new FieldGetter(f.getName(), splitName(f.getName()), f));
                else {
                    for (String name : qs.value())
                        getters.add(new FieldGetter(f.getName(), name, f));
                }
            }
        }
    }

    /**
     * Convert names like "abcDefGhi" to "abc def ghi".
     */
    private String splitName(String url) {
        StringBuilder buf = new StringBuilder(url.length() + 5);
        for (String token : url.split("(?<=[a-z])(?=[A-Z])")) {
            if (!buf.isEmpty())  buf.append(' ');
            buf.append(Introspector.decapitalize(token));
        }
        return buf.toString();
    }

    private String stripGetPrefix(Method m) {
        String n = m.getName();
        if (n.startsWith("get"))
            n = Introspector.decapitalize(n.substring(3));
        return n;
    }


    abstract static class Getter {
        final String url;
        final String searchName;

        protected Getter(String url, String searchName) {
            this.url = url;
            this.searchName = searchName;
        }

        abstract Object get(Object obj);
    }

    static final class MethodGetter extends Getter {
        private final Method method;

        MethodGetter(String url, String searchName, Method method) {
            super(url, searchName);
            this.method = method;
        }

        @Override
        Object get(Object obj) {
            try {
                return method.invoke(obj);
            } catch (IllegalAccessException e) {
                throw toError(e);
            } catch (InvocationTargetException e) {
                Throwable x = e.getTargetException();
                if (x instanceof Error)
                    throw (Error) x;
                if (x instanceof RuntimeException)
                    throw (RuntimeException) x;
                throw new Error(e);
            }
        }
    }

    static final class FieldGetter extends Getter {
        private final Field field;

        FieldGetter(String url, String searchName, Field field) {
            super(url, searchName);
            this.field = field;
        }

        @Override
        Object get(Object obj) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                throw toError(e);
            }
        }
    }

    private static IllegalAccessError toError(IllegalAccessException e) {
        IllegalAccessError iae = new IllegalAccessError();
        iae.initCause(e);
        return iae;
    }

    public void addTo(SearchIndexBuilder builder, final Object instance) {
        for (final Getter getter : getters)
            builder.add(new SearchItem() {
                @Override
                public String getSearchName() {
                    return getter.searchName;
                }

                @Override
                public String getSearchUrl() {
                    return getter.url;
                }

                @Override
                public SearchIndex getSearchIndex() {
                    Object child = getter.get(instance);
                    if (child == null) return SearchIndex.EMPTY;
                    return ((SearchableModelObject) child).getSearchIndex();
                }
            });
    }
}
