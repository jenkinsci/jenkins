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
package hudson.model;

import hudson.ExtensionPoint;
import hudson.ExtensionListView;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.util.List;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Extension point to add icon to <tt>http://server/hudson/manage</tt> page.
 *
 * <p>
 * This is a place for exposing features that are only meant for system admins
 * (whereas features that are meant for Hudson users at large should probably
 * be added to {@link Jenkins#getActions()}.)
 *
 * <p>
 * To register a new instance, put {@link Extension} on your implementation class.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.194
 */
public abstract class ManagementLink implements ExtensionPoint, Action {

    /**
     * Mostly works like {@link Action#getIconFileName()}, except that
     * the expected icon size is 48x48, not 24x24. So if you give
     * just a file name, "/images/48x48" will be assumed.
     *
     * @return
     *      As a special case, return null to exclude this object from the management link.
     *      This is useful for defining {@link ManagementLink} that only shows up under
     *      certain circumstances.
     */
    public abstract String getIconFileName();

    /**
     * Returns a short description of what this link does. This text
     * is the one that's displayed in grey. This can include HTML,
     * although the use of block tags is highly discouraged.
     *
     * Optional.
     */
    public String getDescription() {
        return "";
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In case of {@link ManagementLink}, this value is put straight into the href attribute,
     * so relative paths are interpreted against the root {@link Jenkins} object.
     */
    public abstract String getUrlName();

    /**
     * Allows implementations to request that this link show a confirmation dialog, and use POST if confirmed.
     * Suitable for links which perform an action rather than simply displaying a page.
     * @return true if this link takes an action
     * @see RequirePOST
     * @since 1.512
     */
    public boolean getRequiresConfirmation() {
        return false;
    }

    /**
     * All registered instances.
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access and put {@link Extension} for registration.
     */
    public static final List<ManagementLink> LIST = ExtensionListView.createList(ManagementLink.class);

    /**
     * All regsitered instances.
     */
    public static ExtensionList<ManagementLink> all() {
        return ExtensionList.lookup(ManagementLink.class);
    }

    /**
     * @return permission required for user to access this management link, in addition to {@link Jenkins#ADMINISTER}
     */
    public Permission getRequiredPermission() {
        return null;
    }

    /**
     * Define if the rendered link will use the default GET method or POST.
     * @return true if POST must be used
     * @see RequirePOST
     * @since 1.584
     */
    public boolean getRequiresPOST() {
        return false;
    }
}
