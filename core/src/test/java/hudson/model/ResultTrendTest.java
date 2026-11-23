/*
 * The MIT License
 *
 * Copyright (c) 2026, Urva Gandhi
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("rawtypes")
class ResultTrendTest {

    @Test
    void testSuccessWithoutPreviousBuild() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.SUCCESS, ResultTrend.getResultTrend(run));
    }

    @Test
    void testSuccessWithPreviousSuccess() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.SUCCESS);

        assertEquals(ResultTrend.SUCCESS, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFixedFromFailure() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.FAILURE);

        assertEquals(ResultTrend.FIXED, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFixedFromUnstable() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.UNSTABLE);

        assertEquals(ResultTrend.FIXED, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFixedSkippingAbortedBuild() {
        Run run = mock(Run.class);
        Run aborted = mock(Run.class);
        Run failed = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(aborted);
        when(aborted.getResult()).thenReturn(Result.ABORTED);
        when(aborted.getPreviousBuild()).thenReturn(failed);
        when(failed.getResult()).thenReturn(Result.FAILURE);

        assertEquals(ResultTrend.FIXED, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFixedSkippingNotBuiltBuild() {
        Run run = mock(Run.class);
        Run notBuilt = mock(Run.class);
        Run unstable = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(notBuilt);
        when(notBuilt.getResult()).thenReturn(Result.NOT_BUILT);
        when(notBuilt.getPreviousBuild()).thenReturn(unstable);
        when(unstable.getResult()).thenReturn(Result.UNSTABLE);

        assertEquals(ResultTrend.FIXED, ResultTrend.getResultTrend(run));
    }

    @Test
    void testSuccessSkippingAbortedBuildWhenNoPriorFailure() {
        Run run = mock(Run.class);
        Run aborted = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(aborted);
        when(aborted.getResult()).thenReturn(Result.ABORTED);
        when(aborted.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.SUCCESS, ResultTrend.getResultTrend(run));
    }

    @Test
    void testSuccessWithPreviousBuildNullResult() {
        Run run = mock(Run.class);
        Run building = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getPreviousBuild()).thenReturn(building);
        when(building.getResult()).thenReturn(null);
        when(building.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.SUCCESS, ResultTrend.getResultTrend(run));
    }

    @Test
    void testNowUnstableFromFailure() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.UNSTABLE);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.FAILURE);

        assertEquals(ResultTrend.NOW_UNSTABLE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testStillUnstable() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.UNSTABLE);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.UNSTABLE);

        assertEquals(ResultTrend.STILL_UNSTABLE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testUnstableFromSuccess() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.UNSTABLE);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.SUCCESS);

        assertEquals(ResultTrend.UNSTABLE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testUnstableWithoutPreviousBuild() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.UNSTABLE);
        when(run.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.UNSTABLE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testStillFailing() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.FAILURE);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.FAILURE);

        assertEquals(ResultTrend.STILL_FAILING, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFailureFromSuccess() {
        Run run = mock(Run.class);
        Run prev = mock(Run.class);
        when(run.getResult()).thenReturn(Result.FAILURE);
        when(run.getPreviousBuild()).thenReturn(prev);
        when(prev.getResult()).thenReturn(Result.SUCCESS);

        assertEquals(ResultTrend.FAILURE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testFailureWithoutPreviousBuild() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.FAILURE);
        when(run.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.FAILURE, ResultTrend.getResultTrend(run));
    }

    @Test
    void testAborted() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.ABORTED);

        assertEquals(ResultTrend.ABORTED, ResultTrend.getResultTrend(run));
    }

    @Test
    void testNotBuilt() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.NOT_BUILT);

        assertEquals(ResultTrend.NOT_BUILT, ResultTrend.getResultTrend(run));
    }

    @Test
    void testAbstractBuildOverload() {
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        when(build.getPreviousBuild()).thenReturn(null);

        assertEquals(ResultTrend.SUCCESS, ResultTrend.getResultTrend(build));
    }

    @Test
    void testNullResultThrowsIllegalArgumentException() {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> ResultTrend.getResultTrend(run));
    }

    @ParameterizedTest
    @EnumSource(ResultTrend.class)
    void testDescriptionAndId(ResultTrend trend) {
        assertNotNull(trend.getDescription());
        assertNotNull(trend.getID());
        assertEquals(trend.name().replace('_', ' '), trend.getID());
    }
}
