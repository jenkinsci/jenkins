/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import jenkins.model.AsyncResourceDisposer.Disposable;

import org.junit.Test;

public class AsyncResourceDisposerTest {

    AsyncResourceDisposer disposer = new AsyncResourceDisposer(1);

    @Test
    public void disposeImmediately() throws Exception {
        Disposable disposable = mock(Disposable.class);
        when(disposable.dispose()).thenReturn(true);

        disposer.dispose(disposable);

        Thread.sleep(1000);

        verify(disposable, times(1)).dispose();
        assertTrue(disposer.getProblems().isEmpty());
    }

    @Test
    public void neverDispose() throws Exception {
        final IOException error = new IOException("to be thrown");

        Disposable disposable = mock(Disposable.class);
        when(disposable.dispose()).thenThrow(error);

        disposer.dispose(disposable);

        Thread.sleep(1000);

        Map<Disposable, Exception> problems = disposer.getProblems();

        verify(disposable, atLeast(2)).dispose();
        assertEquals(Collections.singletonMap(disposable, error), problems);
    }

    @Test
    public void postponedDisposal() throws Exception {
        Disposable disposable = mock(Disposable.class);
        when(disposable.dispose()).thenReturn(false, false, false, true);

        disposer.dispose(disposable);

        Thread.sleep(1000);

        verify(disposable, times(4)).dispose();
        assertTrue(disposer.getProblems().isEmpty());
    }

    @Test
    public void combined() throws Exception {

        Disposable noProblem = mock(Disposable.class);
        when(noProblem.dispose()).thenReturn(true);

        final IOException error = new IOException("to be thrown");
        Disposable problem = mock(Disposable.class);
        when(problem.dispose()).thenThrow(error);

        Disposable postponed = mock(Disposable.class);
        when(postponed.dispose()).thenReturn(false, false, false, true);

        disposer.dispose(noProblem);
        disposer.dispose(problem);
        disposer.dispose(noProblem);
        disposer.dispose(postponed);

        Thread.sleep(1000);

        verify(noProblem, times(2)).dispose();
        verify(problem, atLeast(2)).dispose();
        verify(postponed, times(4)).dispose();
        assertEquals(Collections.singletonMap(problem, error), disposer.getProblems());
    }
}
