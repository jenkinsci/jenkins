/*
 * The MIT License
 * 
 * Copyright (c) 2012, Michael O'Cleirigh
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

import hudson.ExtensionPoint;

/**
 * An ACLPermissionOverride provides a permission check that is applied within the base {@link ACL} class
 * ahead of the ACL's own permision checking logic.
 *
 * <p>
 * It provides a mechanism to grant requests that would normally be denied by the standard ACL.  For example to authorize
 * certain plugin callback URL's without having to subclass the AuthorizationStrategy directly.
 *
 * <p>
 * To create a custom permission override type, extend this class and put {@link Extension} on it.
 *
 * @author Michael O'Cleirigh
 *
 */
public abstract class ACLPermissionOverride implements ExtensionPoint {

        /**
         * Checks if the given principle has the given permission.
         *
         * @return
         *      if this method returns non-null, that becomes the final answer regardless of what
         *      {@link AuthorizationStrategy} actually says. If the return value is null,
         *      that indicates no override.
         */
        public abstract Boolean checkPermission (Authentication a, Permission p);

}
