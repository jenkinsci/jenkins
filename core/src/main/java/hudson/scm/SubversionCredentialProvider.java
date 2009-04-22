/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.scm;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.scm.SubversionSCM.DescriptorImpl.Credential;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Extension point for programmatically providing a credential (such as username/password) for
 * Subversion access.
 *
 * <p>
 * Put {@link Extension} on your implementation to have it registered.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.301
 */
public abstract class SubversionCredentialProvider implements ExtensionPoint {
    /**
     * Called whenever Hudson needs to connect to an authenticated subversion repository,
     * to obtain a credential.
     *
     * @param realm
     *      This is a non-null string that represents the realm of authentication.
     * @param url
     *      URL that is being accessed. Never null.
     * @return
     *      null if the implementation doesn't understand the given realm. When null is returned,
     *      Hudson searches other sources of credentials to come up with one.
     */
    public abstract Credential getCredential(SVNURL url, String realm);

    /**
     * All regsitered instances.
     */
    public static ExtensionList<SubversionCredentialProvider> all() {
        return Hudson.getInstance().getExtensionList(SubversionCredentialProvider.class);
    }
}
