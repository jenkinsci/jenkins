/*
 * The MIT License
 *
 * Copyright 2011, OHTAKE Tomohiro.
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

import hudson.model.Action;
import static org.junit.Assert.*;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class FunctionsTest {
    @Test
    @Bug(7725)
    public void testGetActionUrl_mailto(){
        String mailto = "mailto:nobody@example.com";
        String result = Functions.getActionUrl(null, new MockAction(mailto));
        assertEquals(mailto, result);
    }

    private static final class MockAction implements Action {
        private String url;
        public MockAction(String url) {
            this.url = url;
        }
        public String getIconFileName() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        public String getDisplayName() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        public String getUrlName() {
            return url;
        }
    }
}
