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
import static org.mockito.Mockito.*;

public class FunctionsTest {
    @Test
    public void testGetActionUrl_absoluteUriWithAuthority(){
        String[] uris = {
            "http://example.com/foo/bar",
            "https://example.com/foo/bar",
            "ftp://example.com/foo/bar",
            "svn+ssh://nobody@example.com/foo/bar",
        };
        for(String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    @Test
    @Bug(7725)
    public void testGetActionUrl_absoluteUriWithoutAuthority(){
        String[] uris = {
            "mailto:nobody@example.com",
            "mailto:nobody@example.com?subject=hello",
            "javascript:alert('hello')",
        };
        for(String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    private static Action createMockAction(String uri) {
        Action action = mock(Action.class);
        when(action.getUrlName()).thenReturn(uri);
        return action;
    }
}
