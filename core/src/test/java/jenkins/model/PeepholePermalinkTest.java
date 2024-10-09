/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import hudson.model.PermalinkProjectAction;
import hudson.model.Run;
import java.util.stream.Collectors;
import org.junit.Test;

public final class PeepholePermalinkTest {

    @Test
    public void classLoadingDeadlock() throws Exception {
        PeepholePermalink.initialized();
        Thread t = new Thread(() -> {
            assertThat("successfully loaded permalinks",
                PermalinkProjectAction.Permalink.BUILTIN.stream().map(PermalinkProjectAction.Permalink::getId).collect(Collectors.toSet()),
                containsInAnyOrder("lastBuild", "lastStableBuild", "lastSuccessfulBuild", "lastFailedBuild", "lastUnstableBuild", "lastUnsuccessfulBuild", "lastCompletedBuild"));
        });
        t.start();
        new PeepholePermalink() {
            @Override
            public boolean apply(Run<?, ?> run) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getDisplayName() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                throw new UnsupportedOperationException();
            }
        };
        t.join();
    }

}
