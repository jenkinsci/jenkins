/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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

import hudson.model.UserProperty;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Remembers identifiers given by {@link FederatedLoginService} to this user.
 *
 * <p>
 * See {@link FederatedLoginService} for what "identifier" exactly means
 *
 * @author Kohsuke Kawaguchi
 * @since 1.394
 * @see FederatedLoginService
 */
public class FederatedLoginServiceUserProperty extends UserProperty {
    protected final Set<String> identifiers;

    protected FederatedLoginServiceUserProperty(Collection<String> identifiers) {
        this.identifiers = new HashSet<String>(identifiers);
    }

    public boolean has(String identifier) {
        return identifiers.contains(identifier);
    }

    public Collection<String> getIdentifiers() {
        return Collections.unmodifiableSet(identifiers);
    }

    public synchronized void addIdentifier(String id) throws IOException {
        identifiers.add(id);
        user.save();
    }
}
