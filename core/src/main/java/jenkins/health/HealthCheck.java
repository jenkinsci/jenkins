/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.health;

import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * <p>Specifies a health check that is essential for Jenkins to function properly.
 * <br>If this health check fails, monitoring systems will treat the controller as unusable for meaningful tasks.
 * <br>It is assumed that restarting Jenkins can resolve the issue.
 * <p>For instance, low disk space is not a good health check because it can't be solved by restarting Jenkins.
 * <br>Detecting a deadlock in a critical singleton thread would be a good health check as long as associated information (thread dump) is reported somewhere before restarting.
 * @since TODO
 */
@Restricted(Beta.class)
public interface HealthCheck extends ExtensionPoint {

    /**
     * @return the name of the health check. Must be unique among health check implementations.
     * Defaults to the fully qualified class name.
     */
    default String getName() {
        return getClass().getName();
    }

    /**
     * @return true if the health check passed.
     */
    boolean check();
}
