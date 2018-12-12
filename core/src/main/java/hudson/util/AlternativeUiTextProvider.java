/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;

/**
 * Provides the alternative text to be rendered in the UI.
 *
 * <p>
 * In a few limited places in Jenkins, we want to provide an ability for plugins to replace
 * the text to be shown in the UI. Since each such case is rather trivial and can't justify
 * its own extension point, we consolidate all such use cases into a single extension point.
 *
 * <p>
 * Each such overridable UI text can have a context, which represents some information
 * that enables the {@link AlternativeUiTextProvider} to make intelligent decision. This
 * is normally some kind of a model object, such as {@link AbstractProject}.
 *
 * <p>
 * To define a new UI text that can be overridable by {@link AlternativeUiTextProvider},
 * define a constant of {@link Message} (parameterized with the context object type),
 * then call {@link #get(Message, Object)} to obtain a text.
 *
 * <p>
 * To define an implementation that overrides text, define a subtype and put @{@link Extension} on it.
 * See {@link AbstractProject#getBuildNowText()} as an example.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.401
 */
public abstract class AlternativeUiTextProvider implements ExtensionPoint {
    /**
     * Provides an opportunity to override the message text.
     *
     * @param text
     *      Always non-null. Caller should pass in a {@link Message} constant that
     *      represents the text that we are being considered.
     * @param context
     *      Context object. See class javadoc above.
     */
    public abstract <T> String getText(Message<T> text, T context);

    /**
     * All the registered extension point instances.
     */
    public static ExtensionList<AlternativeUiTextProvider> all() {
        return ExtensionList.lookup(AlternativeUiTextProvider.class);
    }

    public static <T> String get(Message<T> text, T context, String defaultValue) {
        String s = get(text,context);
        return s!=null ? s : defaultValue;
    }

    /**
     * Consults all the existing {@link AlternativeUiTextProvider} and return an override, if any,
     * or null.
     */
    public static <T> String get(Message<T> text, T context) {
        for (AlternativeUiTextProvider p : all()) {
            String s = p.getText(text, context);
            if (s!=null)
                return s;
        }
        return null;
    }

    /**
     * Each instance of this class represents a text that can be replaced by {@link AlternativeUiTextProvider}.
     *
     * @param <T>
     *          Context object type. Use {@link Void} to indicate that there's no context.
     */
    public static final class Message<T> {
        // decided not to retain T as Class so that we can have Message<List<Foo>>, for example.

        /**
         * Assists pattern matching in the {@link AlternativeUiTextProvider} implementation.
         */
        @SuppressWarnings({"unchecked"})
        public T cast(Object context) {
            return (T)context;
        }
    }
}
