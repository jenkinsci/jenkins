/*
 * The MIT License
 *
 * Copyright 2015 James Nord.
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

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;
import static org.junit.Assert.assertThat;
import org.jvnet.hudson.test.Issue;

public class AbstractItemSecurityTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Issue("SECURITY-167")
    @Test()
    public void testUpdateByXmlDoesNotProcessForeignResources() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[\n" +
                "  <!ENTITY foo SYSTEM \"file:///\">\n" +
                "]>\n" +
                "<project>\n" +
                "  <description>&foo;</description>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "</project>";

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("security-167");
        project.setDescription("Wibble");
        try {
            project.updateByXml(new StreamSource(new StringReader(xml)));
            // if we didn't fail JAXP has thrown away the entity.
            assertThat(project.getDescription(), isEmptyOrNullString());
        } catch (IOException ex) {
            assertThat(ex.getCause(), not(nullValue()));
            assertThat(ex.getCause().getMessage(), containsString("Refusing to resolve entity"));
        }

    }


    @Issue("SECURITY-167")
    @Test()
    public void testUpdateByXmlDoesNotFail() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<project>\n" +
                "  <description>&amp;</description>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "</project>";

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("security-167");
        project.updateByXml((StreamSource) new StreamSource(new StringReader(xml)));
        assertThat(project.getDescription(), is("&")); // the entity is transformed
    }

}
