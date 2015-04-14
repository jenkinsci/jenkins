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

package hudson;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Test;
import org.xml.sax.SAXException;
import java.io.IOException;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class PluginManagerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void parseRequestedPlugins() throws Exception {
      String XML =
              "<root>\n" +
              "  <stuff plugin='stuff@1.0'>\n" +
              "    <more plugin='other@2.0'>\n" +
              "      <things plugin='stuff@1.2'/>\n" +
              "    </more>\n" +
              "  </stuff>\n" +
              "</root>\n";
        assertEquals("{other=2.0, stuff=1.2}", new LocalPluginManager(tmp.getRoot())
                .parseRequestedPlugins(new StringInputStream(XML)).toString());
    }

    @Issue("SECURITY-167")
    @Test
    public void parseInvalidRequestedPlugins() throws Exception {
        String evilXML = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[<!ENTITY foo SYSTEM \"file:///\">]>\n" +
                "<root>\n" +
                "  <stuff plugin='stuff@1.0'>\n" +
                "&foo;" +
                "    <more plugin='other@2.0'>\n" +
                "      <things plugin='stuff@1.2'/>\n" +
                "    </more>\n" +
                "  </stuff>\n" +
                "</root>\n";

        PluginManager pluginManager = new LocalPluginManager(tmp.getRoot());
        try {
            pluginManager.parseRequestedPlugins(new StringInputStream(evilXML));
            fail("XML contains an external entity, but no exception was thrown.");
        } catch (IOException ex) {
            assertThat(ex.getCause(), instanceOf(SAXException.class));
            assertThat(ex.getCause().getMessage(), containsString("Refusing to resolve entity"));
        }
    }
}
