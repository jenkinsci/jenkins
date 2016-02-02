/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.tasks;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListView;
import hudson.ExtensionPoint;
import hudson.model.User;

import java.util.List;

/**
 * Finds full name off the user when none is specified.
 *
 * <p>
 * This is an extension point of Hudson. Plugins tha contribute new implementation
 * of this class should use {@link Extension} to register the instance into Hudson, like this:
 *
 * <pre>
 * &#64;Extension
 * class MyserNameResolver extends UserNameResolver {
 *   ...
 * }
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.192
 */
public abstract class UserNameResolver implements ExtensionPoint {

    /**
     * Finds full name of the given user.
     *
     * <p>
     * This method is called when a {@link User} without explicitly name is used.
     *
     * <p>
     * When multiple resolvers are installed, they are consulted in order and
     * the search will be over when a name is found by someoene.
     *
     * <p>
     * Since {@link UserNameResolver} is singleton, this method can be invoked concurrently
     * from multiple threads.
     *
     * @return
     *      null if the inference failed.
     */
    public abstract String findNameFor(User u);
    
    public static String resolve(User u) {
        for (UserNameResolver r : all()) {
            String name = r.findNameFor(u);
            if(name!=null) return name;
        }

        return null;
    }

    /**
     * Returns all the registered {@link UserNameResolver} descriptors.
     */
    public static ExtensionList<UserNameResolver> all() {
        return ExtensionList.lookup(UserNameResolver.class);
    }

    /**
     * All registered {@link UserNameResolver} implementations.
     *
     * @deprecated since 2009-02-24.
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    @Deprecated
    public static final List<UserNameResolver> LIST = ExtensionListView.createList(UserNameResolver.class);
}
