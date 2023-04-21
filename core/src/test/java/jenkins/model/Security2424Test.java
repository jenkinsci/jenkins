/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import hudson.model.Failure;
import hudson.model.Messages;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class Security2424Test {
    @Test
    @Issue("SECURITY-2424")
    public void doesNotAcceptNameWithTrailingDot_regular() {
        Failure e = assertThrows(
                "Names with dot should not be accepted",
                Failure.class,
                () -> Jenkins.checkGoodName("job."));
        assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
    }

    @Test
    @Issue("SECURITY-2424")
    public void doesNotAcceptNameWithTrailingDot_withSpaces() {
        Failure e = assertThrows(
                "Names with dot should not be accepted",
                Failure.class,
                () -> Jenkins.checkGoodName("job.   "));
        assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
    }

    @Test
    @Issue("SECURITY-2424")
    public void doesNotAcceptNameWithTrailingDot_exceptIfEscapeHatchIsSet() {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            Jenkins.checkGoodName("job.");
        } finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
    }
}
