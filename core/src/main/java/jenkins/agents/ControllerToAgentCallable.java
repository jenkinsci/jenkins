/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package jenkins.agents;

import hudson.remoting.Callable;
import jenkins.security.Roles;
import jenkins.slaves.RemotingVersionInfo;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link Callable} meant to be serialized then run on an agent.
 * A typical implementation will be a {@link Record}
 * since instance state merely transfers a set of parameters to an agent JVM.
 * <p>Note that the logic within {@link #call} may not use Remoting APIs
 * newer than {@link RemotingVersionInfo#getMinimumSupportedVersion}.
 * (Core and plugin APIs will be identical to those run inside the controller.)
 * @param <V> the return type; note that this must either be defined in your plugin or included in the stock JEP-200 whitelist
 * @since TODO
 */
public interface ControllerToAgentCallable<V, T extends Throwable> extends Callable<V, T>  {

    @Override
    default void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
