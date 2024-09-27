/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

package hudson.cli.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public class ViewOptionHandlerTest {

    @Mock private Setter<View> setter;
    private ViewOptionHandler handler;

    // Hierarchy of views used as a shared fixture:
    // $JENKINS_URL/view/outer/view/nested/view/inner/
    @Mock private View inner;
    @Mock private CompositeView nested;
    @Mock private CompositeView outer;

    private AutoCloseable mocks;

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Before public void setUp() {

        mocks = MockitoAnnotations.openMocks(this);

        handler = new ViewOptionHandler(null, null, setter);

        when(inner.getViewName()).thenReturn("inner");
        when(inner.getDisplayName()).thenCallRealMethod();

        when(nested.getViewName()).thenReturn("nested");
        when(nested.getDisplayName()).thenCallRealMethod();
        when(nested.getView("inner")).thenReturn(inner);

        when(outer.getViewName()).thenReturn("outer");
        when(outer.getDisplayName()).thenCallRealMethod();
        when(outer.getView("nested")).thenReturn(nested);
    }

    @Test public void resolveTopLevelView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            parse("outer");

            verify(setter).addValue(outer);
        }
    }

    private void mockJenkins(MockedStatic<Jenkins> mocked, Jenkins jenkins) {
        mocked.when(Jenkins::get).thenReturn(jenkins);
        when(jenkins.getView("outer")).thenReturn(outer);
        when(jenkins.getDisplayName()).thenReturn("Jenkins");
        when(jenkins.getACL()).thenReturn(new ACL() {
            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission p) {
                return true;
            }
        });
    }

    @Test public void resolveNestedView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            parse("outer/nested");

            verify(setter).addValue(nested);
        }
    }

    @Test public void resolveOuterView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            parse("outer/nested/inner");

            verify(setter).addValue(inner);
        }
    }

    @Test public void ignoreLeadingAndTrailingSlashes() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            parse("/outer/nested/inner/");

            verify(setter).addValue(inner);
        }
    }

    @Test public void reportNonexistentTopLevelView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("missing_view"));
            assertEquals(
                    "No view named missing_view inside view Jenkins",
                    e.getMessage()
            );

            verifyNoInteractions(setter);
        }
    }

    @Test public void reportNonexistentNestedView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("outer/missing_view"));
            assertEquals(
                    "No view named missing_view inside view outer",
                    e.getMessage()
            );

            verifyNoInteractions(setter);
        }
    }

    @Test public void reportNonexistentInnerView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("outer/nested/missing_view"));
            assertEquals(
                    "No view named missing_view inside view nested",
                    e.getMessage()
            );

            verifyNoInteractions(setter);
        }
    }

    @Test public void reportTraversingViewThatIsNotAViewGroup() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> parse("outer/nested/inner/missing"));
            assertEquals(
                    "inner view can not contain views",
                    e.getMessage()
            );

            verifyNoInteractions(setter);
        }
    }

    @Test public void reportEmptyViewNameRequestAsNull() {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            assertNull(handler.getView(""));
            verifyNoInteractions(setter);
        }
    }

    @Test public void reportViewSpaceNameRequestAsIAE() {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            final IllegalArgumentException e = assertThrows("No exception thrown. Expected IllegalArgumentException",
                    IllegalArgumentException.class, () -> assertNull(handler.getView(" ")));
            assertEquals("No view named   inside view Jenkins", e.getMessage());
            verifyNoInteractions(setter);
        }
    }

    @Test public void reportNullViewAsNPE() {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            assertThrows(NullPointerException.class, () -> handler.getView(null));
            verifyNoInteractions(setter);
        }
    }

    @Test public void refuseToReadOuterView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            denyAccessOn(outer);

            AccessDeniedException e = assertThrows(AccessDeniedException.class, () -> parse("outer/nested/inner"));
            assertEquals(
                    "Access denied for: outer",
                    e.getMessage()
            );

            verify(outer).checkPermission(View.READ);

            verifyNoInteractions(nested);
            verifyNoInteractions(inner);
            verifyNoInteractions(setter);
        }
    }

    @Test public void refuseToReadNestedView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            denyAccessOn(nested);

            AccessDeniedException e = assertThrows(AccessDeniedException.class, () -> parse("outer/nested/inner"));
            assertEquals(
                    "Access denied for: nested",
                    e.getMessage()
            );

            verify(nested).checkPermission(View.READ);

            verifyNoInteractions(inner);
            verifyNoInteractions(setter);
        }
    }

    @Test public void refuseToReadInnerView() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mockJenkins(mocked, jenkins);
            denyAccessOn(inner);

            AccessDeniedException e = assertThrows(AccessDeniedException.class, () -> parse("outer/nested/inner"));
            assertEquals(
                    "Access denied for: inner",
                    e.getMessage()
            );

            verify(inner).checkPermission(View.READ);

            verifyNoInteractions(setter);
        }
    }

    private void denyAccessOn(View view) {

        final AccessDeniedException ex = new AccessDeniedException("Access denied for: " + view.getViewName());
        doThrow(ex).when(view).checkPermission(View.READ);
    }

    private void parse(final String... params) throws CmdLineException {
        handler.parseArguments(new Parameters() {
            @Override
            public String getParameter(int idx) {
                return params[idx];
            }

            @Override
            public int size() {
                return params.length;
            }
        });
    }

    private abstract static class CompositeView extends View implements ViewGroup {
        protected CompositeView(String name) {
            super(name);
        }
    }
}
