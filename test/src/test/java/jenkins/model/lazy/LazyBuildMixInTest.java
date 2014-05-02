/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.model.lazy;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;

public class LazyBuildMixInTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Bug(22395)
    @Test public void dropLinksAfterGC() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        assertEquals(b2, b1.getNextBuild());
        assertEquals(b3, b2.getNextBuild());
        assertEquals(null, b3.getNextBuild());
        assertEquals(null, b1.getPreviousBuild());
        assertEquals(b1, b2.getPreviousBuild());
        assertEquals(b2, b3.getPreviousBuild());
        assertEquals(1, BRHF.drop(b1));
        b2.delete();
        FreeStyleBuild b1a = b2.getPreviousBuild();
        assertNotSame(b1, b1a);
        assertEquals(1, b1a.getNumber());
        assertEquals(b3, b1a.getNextBuild());
    }
    /**
     * Unlike the standard {@link SoftReference} this lets us simulate a referent disappearing at a specific time.
     */
    @TestExtension("dropLinksAfterGC") public static final class BRHF implements BuildReference.HolderFactory {
        private static final List<BRH<?>> refs = new ArrayList<BRH<?>>();
        private static final class BRH<R> implements BuildReference.Holder<R> {
            R r;
            BRH(R r) {this.r = r;}
            @Override public R get() {return r;}
        }
        @Override public <R> BuildReference.Holder<R> make(R referent) {
            BRH<R> ref = new BRH<R>(referent);
            synchronized (refs) {
                refs.add(ref);
            }
            return ref;
        }
        /**
         * Simulates garbage collection of a referent.
         * @return how many build references went null as a result
         */
        static int drop(Object o) {
            int count = 0;
            synchronized (refs) {
                for (BRH<?> ref : refs) {
                    if (ref.r == o) {
                        ref.r = null;
                        count++;
                    }
                }
            }
            return count;
        }
    }

    @Bug(20662)
    @Test public void newRunningBuildRelationFromPrevious() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new SleepBuilder(1000));
        FreeStyleBuild b1 = p.scheduleBuild2(0).get();
        assertNull(b1.getNextBuild());
        FreeStyleBuild b2 = p.scheduleBuild2(0).waitForStart();
        assertSame(b2, b1.getNextBuild());
    }
}
