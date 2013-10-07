/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import hudson.model.Descriptor.PropertyType;
import hudson.tasks.Shell;
import static org.junit.Assert.*;

import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.util.concurrent.Callable;

public class DescriptorTest {

    public @Rule JenkinsRule rule = new JenkinsRule();

    @Bug(12307)
    @Test public void getItemTypeDescriptorOrDie() throws Exception {
        Describable<?> instance = new Shell("echo hello");
        Descriptor<?> descriptor = instance.getDescriptor();
        PropertyType propertyType = descriptor.getPropertyType(instance, "command");
        try {
            propertyType.getItemTypeDescriptorOrDie();
            fail("not supposed to succeed");
        } catch (AssertionError x) {
            for (String text : new String[] {"hudson.tasks.CommandInterpreter", "getCommand", "java.lang.String", "collection"}) {
                assertTrue(text + " mentioned in " + x, x.toString().contains(text));
            }
        }
    }

    /**
     * Make sure Descriptor.newInstance gets invoked.
     */
    @Bug(18629) @Test
    public void callDescriptor_newInstance() throws Exception {
        rule.executeOnServer(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                DataBoundBean v = Stapler.getCurrentRequest().bindJSON(DataBoundBean.class, new JSONObject());
                assertEquals(5,v.x);
                return null;
            }
        });
    }

    public static class DataBoundBean extends AbstractDescribableImpl<DataBoundBean> {
        int x;

        // not @DataBoundConstructor
        public DataBoundBean(int x) {
            this.x = x;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<DataBoundBean> {
            @Override
            public String getDisplayName() {
                return "";
            }

            @Override
            public DataBoundBean newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new DataBoundBean(5);
            }
        }

    }

}
