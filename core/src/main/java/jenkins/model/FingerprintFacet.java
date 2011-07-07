/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Fingerprint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.List;

/**
 * Plugin-specific additions to fingerprint information.
 *
 * <p>
 * Each {@link Fingerprint} object records how a particular object (most typically a file,
 * but it can be generalized to anything else that can be turned into a checksum) is used.
 *
 * Traditionally, this "use" is narrowly defined as "seen in build #N of job X", but this
 * extension point generalizes this to allow arbitrary use (such as "deployed to Maven repository",
 * "released to UAT environment", etc.
 *
 * <p>
 * Plugins can just define subtypes of this and {@code fingerprint.getFacets().add(new MyFacet(fingerprint))}
 * to add it to a fingerprint. The intended design is that every time some use happens, you create
 * an instance of new facet and add it.
 *
 * <h2>Views</h2>
 * <h4>main.groovy</h4>
 * <p>
 * This view is rendered into the
 *
 * @author Kohsuke Kawaguchi
 * @since 1.421
 * @see TransientFingerprintFacetFactory
 */
public abstract class FingerprintFacet implements ExtensionPoint {
    private transient Fingerprint fingerprint;

    private final long timestamp;

    /**
     * @param fingerprint
     *      {@link Fingerprint} object to which this facet is going to be added to.
     * @param timestamp
     *      Timestamp when the use happened.
     */
    protected FingerprintFacet(Fingerprint fingerprint, long timestamp) {
        assert fingerprint!=null;
        this.fingerprint = fingerprint;
        this.timestamp = timestamp;
    }

    /**
     * Gets the {@link Fingerprint} that this object belongs to.
     *
     * @return
     *      always non-null.
     */
    public Fingerprint getFingerprint() {
        return fingerprint;
    }

    /**
     * Create action objects to be contributed to the owner {@link Fingerprint}.
     *
     * <p>
     * {@link Fingerprint} calls this method for every {@link FingerprintFacet} that
     * it owns when the rendering is requested.
     */
    public void createActions(List<Action> result) {
    }

    /**
     * Gets the timestamp associated with this facet.
     * The rendering of facets are sorted by their chronological order.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Backdoor for {@link Fingerprint} to set itself to its facets.
     * Public only because this needs to be accessible to {@link Fingerprint}. Do not call this method directly.
     */
    @Restricted(NoExternalUse.class)
    public void _setOwner(Fingerprint fingerprint) {
        assert fingerprint!=null;
        this.fingerprint = fingerprint;
    }
}
