/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.labels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class LabelAtomPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static class LabelAtomPropertyImpl extends LabelAtomProperty {
        public final String abc;

        @DataBoundConstructor
        public LabelAtomPropertyImpl(String abc) {
            this.abc = abc;
        }

        @TestExtension
        public static class DescriptorImpl extends LabelAtomPropertyDescriptor {}
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Test
    public void configRoundtrip() throws Exception {
        LabelAtom foo = j.jenkins.getLabelAtom("foo");
        LabelAtomPropertyImpl old = new LabelAtomPropertyImpl("value");
        foo.getProperties().add(old);
        assertTrue(foo.getConfigFile().exists());
        foo.load(); // make sure load works

        // it should survive the configuration roundtrip
        j.submit(j.createWebClient().goTo("label/foo/configure").getFormByName("config"));
        assertEquals(1,foo.getProperties().size());
        j.assertEqualDataBoundBeans(old, foo.getProperties().get(LabelAtomPropertyImpl.class));
    }
}
