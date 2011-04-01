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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewPropertyTest extends HudsonTestCase {
    public void testRoundtrip() throws Exception {
        ListView foo = new ListView("foo");
        hudson.addView(foo);

        // make sure it renders as optionalBlock
        HtmlForm f = createWebClient().getPage(foo, "configure").getFormByName("viewConfig");
        ((HtmlLabel)f.selectSingleNode(".//LABEL[text()='Debug Property']")).click();
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
}
