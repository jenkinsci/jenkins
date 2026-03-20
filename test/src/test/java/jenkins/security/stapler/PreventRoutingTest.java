/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class PreventRoutingTest extends StaplerAbstractTest {

    @TestExtension
    public static class TargetNull extends AbstractUnprotectedRootAction implements StaplerProxy {
        @Override
        public @CheckForNull String getUrlName() {
            return "target-null";
        }

        @Override
        public Object getTarget() {
            // in case of null, it's "this" that is considered
            return null;
        }

        public Renderable getLegitRoutable() {
            return new Renderable();
        }
    }

    @Test
    // TODO un-ignore once we use a Stapler release with the fix for this
    @Disabled("Does not behave as intended before https://github.com/stapler/stapler/pull/149")
    void getTargetNull_isNotRoutable() {
        assertNotReachable("target-null/legitRoutable");
    }

    @TestExtension
    public static class TargetNewObject extends AbstractUnprotectedRootAction implements StaplerProxy {
        @Override
        public @CheckForNull String getUrlName() {
            return "target-new-object";
        }

        @Override
        public Object getTarget() {
            // Object is not routable
            return new Object();
        }

        public Renderable getLegitRoutable() {
            return new Renderable();
        }
    }

    @Test
    void getTargetNewObject_isNotRoutable() {
        assertNotReachable("target-new-object/legitRoutable");
    }

    @TestExtension
    public static class NotARequest extends AbstractUnprotectedRootAction {
        @Override
        public @CheckForNull String getUrlName() {
            return "not-a-request";
        }

        public Renderable getLegitRoutable() {
            notStaplerGetter(this);
            return new Renderable();
        }

        // just to validate it's ok
        public Renderable getLegitRoutable2() {
            return new Renderable();
        }
    }

    private static void notStaplerGetter(@NonNull Object o) {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req != null) {
            List<Ancestor> ancestors = req.getAncestors();
            if (!ancestors.isEmpty() && ancestors.getLast().getObject() == o) {
                throw HttpResponses.notFound();
            }
        }
    }

    @Test
    void regularGetter_notARequest() throws Exception {
        assertReachable("not-a-request/legitRoutable2");
        assertNotReachable("not-a-request/legitRoutable");
    }
}
