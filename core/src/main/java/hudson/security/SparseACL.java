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

import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.Sid;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import static java.util.logging.Level.FINE;

/**
 * Access control list.
 *
 * @author Kohsuke Kawaguchi
 */
public class SparseACL extends SidACL {
    public static final class Entry {
        // Sid has value-equality semantics
        public final Sid sid;
        public final Permission permission;
        public final boolean allowed;

        public Entry(Sid sid, Permission permission, boolean allowed) {
            this.sid = sid;
            this.permission = permission;
            this.allowed = allowed;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    /**
     * Parent to delegate to. Can be null.
     */
    private ACL parent;

    public SparseACL(ACL parent) {
        this.parent = parent;
    }

    public void add(Entry e) {
        entries.add(e);
    }

    public void add(Sid sid, Permission permission, boolean allowed) {
        add(new Entry(sid,permission,allowed));
    }

    @Override
    public boolean hasPermission(Authentication a, Permission permission) {
        if(a==SYSTEM)   return true;
        Boolean b = _hasPermission(a,permission);
        if(b!=null) return b;

        if(parent!=null) {
            if(LOGGER.isLoggable(FINE))
                LOGGER.fine("hasPermission("+a+","+permission+") is delegating to parent ACL: "+parent);
            return parent.hasPermission(a,permission);
        }

        // the ultimate default is to reject everything
        return false;
    }

    @Override
    protected Boolean hasPermission(Sid p, Permission permission) {
        for( ; permission!=null; permission=permission.impliedBy ) {
            for (Entry e : entries) {
                if(e.permission==permission && e.sid.equals(p))
                    return e.allowed;
            }
        }
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(SparseACL.class.getName());
}
