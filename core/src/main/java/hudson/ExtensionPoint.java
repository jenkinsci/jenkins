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
package hudson;

import jenkins.model.Jenkins;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import jenkins.util.io.OnMaster;

/**
 * Marker interface that designates extensible components
 * in Jenkins that can be implemented by plugins.
 *
 * <p>
 * See respective interfaces/classes for more about how to register custom
 * implementations to Jenkins. See {@link Extension} for how to have
 * Jenkins auto-discover your implementations.
 *
 * <p>
 * This interface is used for auto-generating
 * documentation.
 *
 * @author Kohsuke Kawaguchi
 * @see Plugin
 * @see Extension
 */
public interface ExtensionPoint extends OnMaster {
    /**
     * Used by designers of extension points (direct subtypes of {@link ExtensionPoint}) to indicate that
     * the legacy instances are scoped to {@link Jenkins} instance. By default, legacy instances are
     * static scope.  
     */
    @Target(TYPE)
    @Retention(RUNTIME)
    @interface LegacyInstancesAreScopedToHudson {}
}
