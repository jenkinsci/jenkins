/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.model.version;

import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.concurrent.GuardedBy;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Supplies a VersionOracle by lazily initializing the service at first use.
 * This cannot use the ExtensionList mechanism due to startup ordering and being used by the Jenkins constructor.
 */
@Restricted(NoExternalUse.class)
public class VersionOracleProvider implements Supplier<VersionOracle> {
    private static final Logger LOGGER = Logger.getLogger(VersionOracleProvider.class.getName());

    @GuardedBy("this")
    private volatile VersionOracle oracle;

    @Override
    public VersionOracle get() {
        if (oracle == null) {
            synchronized (this) {
                if (oracle == null) {
                    LOGGER.fine("Initializing VersionOracle");
                    VersionOracle oracle = findMetaInfProvider().orElseGet(DefaultVersionOracle::new);
                    LOGGER.fine(() -> "Found VersionOracle: " + oracle);
                    this.oracle = oracle;
                }
            }
        }
        return oracle;
    }

    private static Optional<VersionOracle> findMetaInfProvider() {
        for (VersionOracle oracle : ServiceLoader.load(VersionOracle.class, getClassLoader())) {
            return Optional.of(oracle);
        }
        return Optional.empty();
    }

    private static ClassLoader getClassLoader() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        return jenkins != null ? jenkins.pluginManager.uberClassLoader : Thread.currentThread().getContextClassLoader();
    }
}
