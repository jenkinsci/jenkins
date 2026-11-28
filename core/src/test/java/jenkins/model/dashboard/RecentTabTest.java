/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecentTabTest {

    @Test
    void timeSpansGrowInSteps() {
        assertThat(RecentTab.roughTimeSpan(-5), is("less than a minute"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.SECONDS.toMillis(59)), is("less than a minute"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.SECONDS.toMillis(61)), is("1 min"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.SECONDS.toMillis(9 * 60 + 59)), is("9 min"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.MINUTES.toMillis(14)), is("10 min"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.MINUTES.toMillis(59)), is("55 min"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.MINUTES.toMillis(119)), is("1 hr"));
        assertThat(RecentTab.roughTimeSpan(TimeUnit.HOURS.toMillis(49)), is("2 days"));
    }

    @Test
    void ifNoneMatchComparesEachTagWeakly() {
        assertThat(RecentTab.matchesAny(null, "\"a\""), is(false));
        assertThat(RecentTab.matchesAny("\"a\"", "\"a\""), is(true));
        assertThat(RecentTab.matchesAny("W/\"a\"", "\"a\""), is(true));
        assertThat(RecentTab.matchesAny("\"b\", W/\"a\"", "\"a\""), is(true));
        assertThat(RecentTab.matchesAny("*", "\"a\""), is(true));
        assertThat(RecentTab.matchesAny("\"b\"", "\"a\""), is(false));
        assertThat(RecentTab.matchesAny("\"ab\"", "\"a\""), is(false));
    }
}
