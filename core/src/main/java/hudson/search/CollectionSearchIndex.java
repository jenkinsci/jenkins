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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.ACL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

/**
 * {@link SearchIndex} built on a {@link Map}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CollectionSearchIndex<SMT extends SearchableModelObject> implements SearchIndex {
    /**
     * Gets a single item that exactly matches the given key.
     */
    protected abstract SearchItem get(String key);

    /**
     * Returns all items in the map.
     * The collection can include null items.
     */
    protected abstract Collection<SMT> all();

    @NonNull
    protected Iterable<SMT> allAsIterable() {
        Collection<SMT> all = all();
        return all == null ? Collections.emptySet() : all;
    }

    @Override
    public void find(String token, List<SearchItem> result) {
        SearchItem p = get(token);
        if (p != null)
            result.add(p);
    }

    @Override
    public void suggest(String token, List<SearchItem> result) {
        boolean isCaseSensitive = UserSearchProperty.isCaseInsensitive();
        if (isCaseSensitive) {
          token = token.toLowerCase();
        }

        // Determine if the current user should be restricted from seeing other users names
        boolean restrictUserNames = isUserNameRestrictionRequired();

        for (SMT o : allAsIterable()) {
            String name = getName(o);
            if (isCaseSensitive)
                name = name.toLowerCase();

            if (name.contains(token) && (!restrictUserNames ||  ( !isUserItem(o))))
                result.add(o);
        }
    }

    protected String getName(SMT o) {
        return o.getDisplayName();
    }

    protected boolean isUserItem(SMT o) {
        if (o instanceof User) {
            return true;
        }
        return false;
    }


    private boolean isUserNameRestrictionRequired() {
        // Get the current user's authentication
        Authentication authentication = Jenkins.getAuthentication();

        // Retrieve ACL and check for RESTRICTED_READ permission
        ACL acl = Jenkins.get().getACL();
        boolean hasRestrictedReadPermission = acl.hasPermission(authentication, Jenkins.RESTRICTED_READ);
        boolean hasReadPermission = acl.hasPermission(authentication, Jenkins.READ);
        boolean hasAdminPermission = acl.hasPermission(authentication, Jenkins.ADMINISTER);

        // Restrict only if the user has RESTRICTED_READ and does not have READ or ADMINISTER
        return hasRestrictedReadPermission && !hasReadPermission && !hasAdminPermission;
    }
}
