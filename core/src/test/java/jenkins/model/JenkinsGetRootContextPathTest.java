/*
 * The MIT License
 *
 * Copyright (c) 2013 RedHat Inc.
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class JenkinsGetRootContextPathTest {

    private Jenkins jenkins;

    @Before
    public void setUp() {

        jenkins = mock(Jenkins.class);
        when(jenkins.getRootContextPath()).thenCallRealMethod();
    }

    @Test
    public void getNullPathForNullUrl() {

        forJenkinsAt(null);
        rootContextPathIs(null);
    }

    @Test
    public void getNullPathForMissconfigured() {

        forJenkinsAt("jenkins");
        rootContextPathIs(null);
    }

    @Test
    public void getSlashForEmptyPath() {

        forJenkinsAt("http://localhost:8080/");
        rootContextPathIs("/");
    }

    @Test
    public void getPath() {

        forJenkinsAt("http://localhost:8080/jenkins/");
        rootContextPathIs("/jenkins/");
    }

    private void forJenkinsAt(final String rootUrl) {

        when(jenkins.getRootUrl()).thenReturn(rootUrl);
    }

    private void rootContextPathIs(final String expectedRootUrl) {

        assertThat(jenkins.getRootContextPath(), equalTo(expectedRootUrl));
    }
}
