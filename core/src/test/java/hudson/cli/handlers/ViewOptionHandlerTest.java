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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import hudson.model.ViewGroup;
import hudson.model.View;

import jenkins.model.Jenkins;

import org.acegisecurity.AccessDeniedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest(Jenkins.class)
@RunWith(PowerMockRunner.class)
public class ViewOptionHandlerTest {

    @Mock private Setter<View> setter;
    private ViewOptionHandler handler;

    // Hierarchy of views used as a shared fixture:
    // $JENKINS_URL/view/outer/view/nested/view/inner/
    @Mock private View inner;
    @Mock private CompositeView nested;
    @Mock private CompositeView outer;
    @Mock private Jenkins jenkins;

    @Before public void setUp() {

        MockitoAnnotations.initMocks(this);

        handler = new ViewOptionHandler(null, null, setter);

        when(inner.getViewName()).thenReturn("inner");
        when(inner.getDisplayName()).thenCallRealMethod();

        when(nested.getViewName()).thenReturn("nested");
        when(nested.getDisplayName()).thenCallRealMethod();
        when(nested.getView("inner")).thenReturn(inner);

        when(outer.getViewName()).thenReturn("outer");
        when(outer.getDisplayName()).thenCallRealMethod();
        when(outer.getView("nested")).thenReturn(nested);

        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getView("outer")).thenReturn(outer);
        when(jenkins.getDisplayName()).thenReturn("Jenkins");
    }

    @Test public void resolveTopLevelView() throws Exception {

        parse("outer");

        verify(setter).addValue(outer);
    }

    @Test public void resolveNestedView() throws Exception {

        parse("outer/nested");

        verify(setter).addValue(nested);
    }

    @Test public void resolveOuterView() throws Exception {

        parse("outer/nested/inner");

        verify(setter).addValue(inner);
    }

    @Test public void ignoreLeadingAndTrailingSlashes() throws Exception {

        parse("/outer/nested/inner/");

        verify(setter).addValue(inner);
    }

    @Test public void reportNonexistentTopLevelView() throws Exception {

        assertEquals(
                "No view named missing_view inside view Jenkins",
                parseFailedWith(CmdLineException.class, "missing_view")
        );

        verifyZeroInteractions(setter);
    }

    @Test public void reportNonexistentNestedView() throws Exception {

        assertEquals(
                "No view named missing_view inside view outer",
                parseFailedWith(CmdLineException.class, "outer/missing_view")
        );

        verifyZeroInteractions(setter);
    }

    @Test public void reportNonexistentInnerView() throws Exception {

        assertEquals(
                "No view named missing_view inside view nested",
                parseFailedWith(CmdLineException.class, "outer/nested/missing_view")
        );

        verifyZeroInteractions(setter);
    }

    @Test public void reportTraversingViewThatIsNotAViewGroup() throws Exception {

        assertEquals(
                "inner view can not contain views",
                parseFailedWith(CmdLineException.class, "outer/nested/inner/missing")
        );

        verifyZeroInteractions(setter);
    }

    @Test public void refuseToReadOuterView() throws Exception {

        denyAccessOn(outer);

        assertEquals(
                "Access denied for: outer",
                parseFailedWith(CmdLineException.class, "outer/nested/inner")
        );

        verify(outer).checkPermission(View.READ);

        verifyZeroInteractions(nested);
        verifyZeroInteractions(inner);
        verifyZeroInteractions(setter);
    }

    @Test public void refuseToReadNestedView() throws Exception {

        denyAccessOn(nested);

        assertEquals(
                "Access denied for: nested",
                parseFailedWith(CmdLineException.class, "outer/nested/inner")
        );

        verify(nested).checkPermission(View.READ);

        verifyZeroInteractions(inner);
        verifyZeroInteractions(setter);
    }

    @Test public void refuseToReadInnerView() throws Exception {

        denyAccessOn(inner);

        assertEquals(
                "Access denied for: inner",
                parseFailedWith(CmdLineException.class, "outer/nested/inner")
        );

        verify(inner).checkPermission(View.READ);

        verifyZeroInteractions(setter);
    }

    private void denyAccessOn(View view) {

        final AccessDeniedException ex = new AccessDeniedException("Access denied for: " + view.getViewName());
        doThrow(ex).when(view).checkPermission(View.READ);
    }

    private String parseFailedWith(Class<? extends Exception> type, final String... params) throws Exception {

        try {
            parse(params);

        } catch (Exception ex) {

            if (!type.isAssignableFrom(ex.getClass())) throw ex;

            return ex.getMessage();
        }

        fail("No exception thrown. Expected " + type.getClass());
        return null;
    }

    private void parse(final String... params) throws CmdLineException {
        handler.parseArguments(new Parameters() {
            public String getParameter(int idx) throws CmdLineException {
                return params[idx];
            }
            public int size() {
                return params.length;
            }
        });
    }

    private static abstract class CompositeView extends View implements ViewGroup {
        protected CompositeView(String name) {
            super(name);
        }
    }
}
