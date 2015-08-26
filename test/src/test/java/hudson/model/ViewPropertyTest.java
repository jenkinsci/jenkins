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

import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewPropertyTest extends HudsonTestCase {
    public void testRoundtrip() throws Exception {
        ListView foo = new ListView("foo");
        jenkins.addView(foo);

        // make sure it renders as optionalBlock
        HtmlForm f = createWebClient().getPage(foo, "configure").getFormByName("viewConfig");
        ((HtmlLabel) DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='Debug Property']")).click();
        submit(f);
        ViewPropertyImpl vp = foo.getProperties().get(ViewPropertyImpl.class);
        assertEquals("Duke",vp.name);

        // make sure it roundtrips correctly
        vp.name = "Kohsuke";
        configRoundtrip(foo);
        ViewPropertyImpl vp2 = foo.getProperties().get(ViewPropertyImpl.class);
        assertNotSame(vp,vp2);
        assertEqualDataBoundBeans(vp,vp2);
    }

    public static class ViewPropertyImpl extends ViewProperty {
        public String name;

        @DataBoundConstructor
        public ViewPropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "Debug Property";
            }
        }
    }

    public void testInvisibleProperty() throws Exception {
        ListView foo = new ListView("foo");
        jenkins.addView(foo);

        // test the rendering (or the lack thereof) of an invisible property
        configRoundtrip(foo);
        assertNull(foo.getProperties().get(InvisiblePropertyImpl.class));

        // do the same but now with a configured instance
        InvisiblePropertyImpl vp = new InvisiblePropertyImpl();
        foo.getProperties().add(vp);
        configRoundtrip(foo);
        assertSame(vp,foo.getProperties().get(InvisiblePropertyImpl.class));
    }

    public static class InvisiblePropertyImpl extends ViewProperty {
        InvisiblePropertyImpl() {
        }

        @Override
        public ViewProperty reconfigure(StaplerRequest req, JSONObject form) throws FormException {
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }
}
