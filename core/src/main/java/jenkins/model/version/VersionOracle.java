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

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Strategy to look up the version of Jenkins currently running. Custom implementations may return fake version
 * numbers or guard access to the version with an authorization check. The configured oracle is obtained via
 * {@link java.util.ServiceLoader}, so custom implementations should be annotated with {@link org.kohsuke.MetaInfServices}.
 * If no oracle is available, {@link DefaultVersionOracle} is used. Custom oracles may wish to delegate to
 * DefaultVersionOracle.
 *
 * @since TODO
 */
public interface VersionOracle {
    /**
     * Returns the version of Jenkins running or empty if it can't be calculated or if it can't be displayed.
     */
    @Nonnull
    Optional<String> getVersion();
}
