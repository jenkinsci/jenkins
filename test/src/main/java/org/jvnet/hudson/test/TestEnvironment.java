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
package org.jvnet.hudson.test;


import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.junit.runner.Description;

/**
 * TODO: deprecate this, and just consolidate this to {@link HudsonTestCase}.
 * We can then pin down the current HudsonTestCase to the thread for easier access.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestEnvironment {
    /**
     * Current test case being run (null for a JUnit 4 test).
     */
    public final @CheckForNull HudsonTestCase testCase;

    /**
     * Current test case being run (null for a JUnit 3 test).
     */
    private final @CheckForNull Description description;

    public final TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();

    public TestEnvironment(@Nonnull HudsonTestCase testCase) {
        this.testCase = testCase;
        this.description = null;
    }

    public TestEnvironment(@Nonnull Description description) {
        this.testCase = null;
        this.description = description;
    }

    /**
     * Current test case being run (works for JUnit 3 or 4).
     * Warning: {@link Description#getTestClass} is currently broken in some environments (claimed fixed in JUnit 4.11). Use {@link Description#getClassName} instead.
     */
    public @Nonnull Description description() {
        if (description != null) {
            return description;
        } else {
            assert testCase != null;
            // Initialize lazily; testCase.getName() may be null in the TestEnvironment constructor.
            return Description.createTestDescription(testCase.getClass(), testCase.getName());
        }
    }

    public void pin() {
        CURRENT = this;
    }

    public void dispose() throws IOException, InterruptedException {
        temporaryDirectoryAllocator.dispose();
        CURRENT = null;
    }

    /**
     * We used to use {@link InheritableThreadLocal} here, but it turns out this is not reliable,
     * especially in the {@link Computer#threadPoolForRemoting}, where threads can inherit
     * the wrong test environment depending on when it's created.
     *
     * <p>
     * Since the rest of Hudson still relies on static {@link jenkins.model.Jenkins#theInstance}, changing this
     * to a static field for now shouldn't cause any problem. 
     */
    private static TestEnvironment CURRENT;

    public static TestEnvironment get() {
        return CURRENT;
    }
}
