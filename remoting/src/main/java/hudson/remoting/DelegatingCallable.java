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
package hudson.remoting;

/**
 * {@link Callable} that nominates another claassloader for serialization.
 *
 * <p>
 * For various reasons, one {@link Callable} object (and all the objects reachable from it) is
 * serialized by one classloader.
 * By default, the classloader that loaded {@link Callable} object itself is used,
 * but when {@link Callable} object refers to other objects that are loaded by other classloaders,
 * this will fail to deserialize on the remote end.
 *
 * <p>
 * In such a case, implement this interface, instead of plain {@link Callable} and
 * return a classloader that can see all the classes.
 *
 * In case of Hudson, {@code PluginManager.uberClassLoader} is a good candidate.  
 *
 * @author Kohsuke Kawaguchi
 */
public interface DelegatingCallable<V,T extends Throwable> extends Callable<V,T> {
    ClassLoader getClassLoader();
}
