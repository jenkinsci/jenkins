/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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
package jenkins.util.java;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.*;
import org.junit.Test;
import org.jvnet.hudson.test.For;

@For(JavaUtils.class)
public class JavaUtilsTest {

    @Test
    public void verifyJava8() {
        assumeTrue("Test is for Java 8 only", System.getProperty("java.version").startsWith("1."));
        assertFalse("isRunningWithPostJava8() should return false on Java 8 and below", JavaUtils.isRunningWithPostJava8());
    }

    @Test
    public void verifyPostJava8() {
        assumeFalse("Test is for Java 9+ only", System.getProperty("java.version").startsWith("1."));
        assertTrue("isRunningWithPostJava8() should return true on Java 9 and above", JavaUtils.isRunningWithPostJava8());
    }
}
