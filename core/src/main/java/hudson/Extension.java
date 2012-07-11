/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import jenkins.YesNoMaybe;
import net.java.sezpoz.Indexable;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static jenkins.YesNoMaybe.MAYBE;

/**
 * Marks a field, a method, or a class for automatic discovery, so that Hudson can locate
 * implementations of {@link ExtensionPoint}s automatically.
 *
 * <p>
 * (In contrast, in earlier Hudson, the registration was manual.)
 *
 * <p>
 * In a simplest case, put this on your class, and Hudson will create an instance of it
 * and register it to the appropriate {@link ExtensionList}.
 *
 * <p>
 * If you'd like Hudson to call
 * a factory method instead of a constructor, put this annotation on your static factory
 * method. Hudson will invoke it and if the method returns a non-null instance, it'll be
 * registered. The return type of the method is used to determine which {@link ExtensionList}
 * will get the instance.
 *
 * Finally, you can put this annotation on a static field if the field contains a reference
 * to an instance that you'd like to register.
 *
 * <p>
 * This is the default way of having your implementations auto-registered to Hudson,
 * but Hudson also supports arbitrary DI containers for hosting your implementations.
 * See {@link ExtensionFinder} for more details.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see <a href="http://sezpoz.java.net/">SezPoz</a>
 * @see ExtensionFinder
 * @see ExtensionList
 */
@Indexable
@Retention(RUNTIME)
@Target({TYPE, FIELD, METHOD})
@Documented
public @interface Extension {
    /**
     * Used for sorting extensions.
     *
     * Extensions will be sorted in the descending order of the ordinal.
     * This is a rather poor approach to the problem, so its use is generally discouraged.
     *
     * @since 1.306
     */
    double ordinal() default 0;

    /**
     * If an extension is optional, don't log any class loading errors when reading it.
     * @since 1.358
     */
    boolean optional() default false;

    /**
     * Marks whether this extension works with dynamic loading of a plugin.
     *
     * <p>
     * "Yes" indicates an explicit sign-off from the developer indicating this component supports that.
     * Similarly, "No" indicates that this extension is known not to support it, which forces Jenkins
     * not to offer dynamic loading as an option.
     *
     * <p>
     * The "MayBe" value indicates that there's no such explicit sign-off. So the dynamic loading may or may not
     * work.
     *
     * <p>
     * If your plugin contains any extension that has dynamic loadability set to NO, then Jenkins
     * will prompt the user to restart Jenkins to have the plugin take effect. If every component
     * is marked as YES, then Jenkins will simply dynamic load the plugin without asking the user.
     * Otherwise, Jenkins will ask the user if he wants to restart, or go ahead and dynamically deploy.
     *
     * @since 1.442
     */
    YesNoMaybe dynamicLoadable() default MAYBE;
}
