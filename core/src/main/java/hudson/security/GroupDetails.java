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
package hudson.security;

import org.acegisecurity.userdetails.UserDetails;

import java.util.Set;

/**
 * Represents the details of a group.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.280
 * @see UserDetails
 */
public abstract class GroupDetails {
    /**
     * Returns the name of the group.
     *
     * @return never null.
     */
    public abstract String getName();

    /**
     * Returns the human-readable name used for rendering in HTML.
     *
     * <p>
     * This may contain arbitrary character, and it can change.
     *
     * @return never null.
     */
    public String getDisplayName() {
        return getName();
    }

    /**
     * Returns the members of the group, or {@code null} if the members were not retrieved. The results of this method
     * are not live, they represent the membership at the time the {@link GroupDetails} was instantiated. As fetching
     * the membership of a group can be an expensive operation, it is preferential to use the
     * {@link SecurityRealm#loadGroupByGroupname(String, boolean)} method to retrieve {@link GroupDetails} in those
     * cases where you want to try and retrieve the members of the group, though even that method does not guarantee
     * to retrieve the members of a group as the backing {@link SecurityRealm} implementation may not support such
     * a query.
     *
     * @return the members of the group at the point in time when the {@link GroupDetails} were retrieved, or
     * {@code null} if the members were not retrieved.
     * @since 1.549
     */
    public Set<String> getMembers() {
        return null;
    }
}
