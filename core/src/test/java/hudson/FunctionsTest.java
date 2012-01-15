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
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.core.classloader.annotations.PrepareForTest;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
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

    @Test
    @PrepareForTest(Stapler.class)
    public void testGetActionUrl_absolutePath() throws Exception{
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        String[] paths = {
            "/",
            "/foo/bar",
        };
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        for(String path : paths) {
            String result = Functions.getActionUrl(null, createMockAction(path));
            assertEquals(contextPath + path, result);
        }
    }

    @Test
    @PrepareForTest(Stapler.class)
    public void testGetActionUrl_relativePath() throws Exception{
        String contextPath = "/jenkins";
        String itUrl = "iturl/";
        StaplerRequest req = createMockRequest(contextPath);
        String[] paths = {
            "foo/bar",
            "./foo/bar",
            "../foo/bar",
        };
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        for(String path : paths) {
            String result = Functions.getActionUrl(itUrl, createMockAction(path));
            assertEquals(contextPath + "/" + itUrl + path, result);
        }
    }

    private static Action createMockAction(String uri) {
        Action action = mock(Action.class);
        when(action.getUrlName()).thenReturn(uri);
        return action;
    }

    private static StaplerRequest createMockRequest(String contextPath) {
        StaplerRequest req = mock(StaplerRequest.class);
        when(req.getContextPath()).thenReturn(contextPath);
        return req;
    }
}
