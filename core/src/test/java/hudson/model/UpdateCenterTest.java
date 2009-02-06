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
package hudson.model;

import junit.framework.TestCase;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;

/**
 * Quick test for {@link UpdateCenter}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenterTest extends TestCase {
    public void testData() throws IOException {
        // check if we have the internet connectivity. See HUDSON-2095
        try {
            new URL("https://hudson.dev.java.net/").openStream();
        } catch (IOException e) {
            System.out.println("Skipping this test. No internet connectivity");
            return;
        }

        URL url = new URL("https://hudson.dev.java.net/update-center.json?version=build");
        String jsonp = IOUtils.toString(url.openStream());
        String json = jsonp.substring(jsonp.indexOf('(')+1,jsonp.lastIndexOf(')'));

        UpdateCenter uc = new UpdateCenter(null);
        UpdateCenter.Data data = uc.new Data(JSONObject.fromObject(json));
        assertTrue(data.core.url.startsWith("https://hudson.dev.java.net/"));
        assertTrue(data.plugins.containsKey("rake"));
        System.out.println(data.core.url);
    }
}
