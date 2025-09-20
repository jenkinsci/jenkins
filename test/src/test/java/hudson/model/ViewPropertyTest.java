/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.sf.json.JSONObject;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ViewPropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testRoundtrip() throws Exception {
        ListView foo = new ListView("foo");
        j.jenkins.addView(foo);

        // make sure it renders as optionalBlock
        HtmlForm f = j.createWebClient().getPage(foo, "configure").getFormByName("viewConfig");
        ((HtmlLabel) DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='ViewPropertyImpl']")).click();
        j.submit(f);
        ViewPropertyImpl vp = foo.getProperties().get(ViewPropertyImpl.class);
        assertEquals("Duke", vp.name);

        // make sure it roundtrips correctly
        vp.name = "Kohsuke";
        j.configRoundtrip(foo);
        ViewPropertyImpl vp2 = foo.getProperties().get(ViewPropertyImpl.class);
        assertNotSame(vp, vp2);
        j.assertEqualDataBoundBeans(vp, vp2);
    }

    public static class ViewPropertyImpl extends ViewProperty {
        public String name;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public ViewPropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {}
    }

    @Test
    void testInvisibleProperty() throws Exception {
        ListView foo = new ListView("foo");
        j.jenkins.addView(foo);

        // test the rendering (or the lack thereof) of an invisible property
        j.configRoundtrip(foo);
        assertNull(foo.getProperties().get(InvisiblePropertyImpl.class));

        // do the same but now with a configured instance
        InvisiblePropertyImpl vp = new InvisiblePropertyImpl();
        foo.getProperties().add(vp);
        j.configRoundtrip(foo);
        assertSame(vp, foo.getProperties().get(InvisiblePropertyImpl.class));
    }

    public static class InvisiblePropertyImpl extends ViewProperty {
        InvisiblePropertyImpl() {
        }

        @Override
        public ViewProperty reconfigure(StaplerRequest2 req, JSONObject form) {
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {}
    }
}
