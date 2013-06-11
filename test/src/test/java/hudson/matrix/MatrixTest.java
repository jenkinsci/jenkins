/*
 * The MIT License
 * 
 * Copyright (c) 2010-2011, Alan Harder
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
package hudson.matrix;

import hudson.model.Item;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import java.util.Collections;
import org.acegisecurity.context.SecurityContextHolder;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;

/**
 * @author Alan Harder
 */
public class MatrixTest extends HudsonTestCase {

    /**
     * Test that spaces are encoded as %20 for project name, axis name and axis value.
     */
    public void testSpaceInUrl() {
        MatrixProject mp = new MatrixProject("matrix test");
        MatrixConfiguration mc = new MatrixConfiguration(mp, Combination.fromString("foo bar=baz bat"));
        assertEquals("job/matrix%20test/", mp.getUrl());
        assertEquals("job/matrix%20test/foo%20bar=baz%20bat/", mc.getUrl());
    }
    
    /**
     * Test that project level permissions apply to child configurations as well.
     */
    @Bug(9293)
    public void testConfigurationACL() throws Exception {
        jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy());
        MatrixProject mp = createMatrixProject();
        mp.setAxes(new AxisList(new Axis("foo", "a", "b")));
        MatrixConfiguration mc = mp.getItem("foo=a");
        assertNotNull(mc);
        SecurityContextHolder.clearContext();
        assertFalse(mc.getACL().hasPermission(Item.READ));
        mp.addProperty(new AuthorizationMatrixProperty(
                Collections.singletonMap(Item.READ, Collections.singleton("anonymous"))));
        // Project-level permission should apply to single configuration too:
        assertTrue(mc.getACL().hasPermission(Item.READ));
    }

    public void testApi() throws Exception {
        MatrixProject project = createMatrixProject();
        project.setAxes(new AxisList(
                new Axis("FOO", "abc", "def"),
                new Axis("BAR", "uvw", "xyz")));
        XmlPage xml = new WebClient().goToXml(project.getUrl() + "api/xml");
        assertEquals(4, xml.getByXPath("//matrixProject/activeConfiguration").size());
    }
}
