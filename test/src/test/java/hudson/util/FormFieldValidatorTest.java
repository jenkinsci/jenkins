/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidatorTest.BrokenFormValidatorBuilder.DescriptorImpl;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.WithPlugin;

/**
 * @author Kohsuke Kawaguchi
 */
public class FormFieldValidatorTest extends HudsonTestCase {
    @Bug(2771)
    @WithPlugin("tasks.hpi")
    public void test2771() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        new WebClient().getPage(p,"configure");
    }

    public static class BrokenFormValidatorBuilder extends Builder {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public void doCheckXyz() {
                throw new Error("doCheckXyz is broken");
            }

            public String getDisplayName() {
                return "I have broken form field validation";
            }
        }
    }

    /**
     * Make sure that the validation methods are really called by testing a negative case.
     */
    @Bug(3382)
    public void testNegative() throws Exception {
        DescriptorImpl d = new DescriptorImpl();
        Publisher.all().add(d);
        try {
            FreeStyleProject p = createFreeStyleProject();
            new WebClient().getPage(p,"configure");
            fail("should have failed");
        } catch(AssertionError e) {
            if(e.getMessage().contains("doCheckXyz is broken"))
                ; // expected
            else
                throw e;
        } finally {
            Publisher.all().remove(d);
        }
    }

}
