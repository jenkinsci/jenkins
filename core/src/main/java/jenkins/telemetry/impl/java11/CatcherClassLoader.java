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

package jenkins.telemetry.impl.java11;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CatcherClassLoader extends ClassLoader {

    Logger LOGGER = Logger.getLogger(CatcherClassLoader.class.getName());

    // TODO: Remove when tested on ATH
    // Where this CL was created for avoiding adding it to the chain more than once
    public String creationClass;
    public String creationMethod;
    public int creationLine;


    public CatcherClassLoader(ClassLoader parent) {
        super(parent);

        // TODO: Remove when tested on ATH
        // Audit where I was created to avoid having too much of this objects on the classloader chain, only one is
        // needed and at the end of the chain.
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length >= 3) {
            creationClass = stackTraceElements[2].getClassName();
            creationMethod = stackTraceElements[2].getMethodName();
            creationLine = stackTraceElements[2].getLineNumber();
        }
        warnIfAlreadyInTheChain();
    }


    /**
     * Usually, the {@link ClassLoader} calls its parent and finally this method. So if we are here, it's the last
     * element of the chain. It doesn't happen in {@link jenkins.util.AntClassLoader} so it has an special management
     * on {@link hudson.ClassicPluginStrategy}
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassNotFoundException e = new ClassNotFoundException(name);
        MissingClassTelemetry.reportExceptionIfAllowedAndConvenient(name, e);
        throw e;
    }


    // TODO: Remove when tested on ATH
    private void warnIfAlreadyInTheChain() {
        boolean found = false;
        ClassLoader parent = this.getParent();
        while (parent != null && !found) {
            if (parent instanceof CatcherClassLoader) {
                // Only look for the immediate parent of the same class, the grandparent was reported when creating the
                // parent, so no need to report it again.
                found = true;
                CatcherClassLoader parentClass = (CatcherClassLoader) parent;
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "The {0} was added on the chain on {1}#{2}[{3}] and on {4}#{5}[{6}]. Better remove the addition on {7}#{8}[{9}]",
                            new Object[] {getClass().getName(), creationClass, creationMethod, creationLine,
                                    parentClass.creationClass, parentClass.creationMethod, parentClass.creationLine,
                                    parentClass.creationClass, parentClass.creationMethod, parentClass.creationLine});
                }
            }
            parent = parent.getParent();
        }
    }
}