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

import hudson.ExtensionPoint;
import hudson.Plugin;
import hudson.scm.SCM;
import hudson.scm.CVSSCM;
import hudson.scm.SubversionSCM;
import hudson.model.User;
import hudson.model.AbstractProject;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Finds full name off the user when none is specified.
 *
 * <p>
 * This is an extension point of Hudson. Plugins tha contribute new implementation
 * of this class must register it to {@link #LIST}. The typical way to do this is:
 *
 * <pre>
 * class PluginImpl extends {@link Plugin} {
 *   public void start() {
 *     ...
 *     UserNameResolver.LIST.add(new UserNameResolver());
 *   }
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
        for (UserNameResolver r : LIST) {
            String name = r.findNameFor(u);
            if(name!=null) return name;
        }

            return null;
    }

    /**
     * All registered {@link UserNameResolver} implementations.
     */
    public static final List<UserNameResolver> LIST = new CopyOnWriteArrayList<UserNameResolver>();

}
