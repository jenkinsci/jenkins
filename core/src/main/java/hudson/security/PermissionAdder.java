/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.ExtensionPoint;
import hudson.model.User;

/**
 * Service which can add permissions for a given user to the configured authorization strategy.
 * Useful e.g. to make the first user created in the system be automatically granted administer privilege.
 * @since 1.535
 */
public abstract class PermissionAdder implements ExtensionPoint {

    /**
     * Called to try to give a user global permission.
     * @param strategy the configured authorization strategy
     * @param user a user
     * @param perm a permission to grant, such as {@link Jenkins#ADMINISTER}
     * @return true if the permission was added, false if this service is incapable of handling it
     */
    public abstract boolean add(AuthorizationStrategy strategy, User user, Permission perm);

}
