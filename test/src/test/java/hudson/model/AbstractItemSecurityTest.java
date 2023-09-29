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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class AbstractItemSecurityTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Issue("SECURITY-167")
    @Test
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
            project.updateByXml((Source) new StreamSource(new StringReader(xml)));
            // if we didn't fail JAXP has thrown away the entity.
            assertThat(project.getDescription(), emptyOrNullString());
        } catch (IOException ex) {
            assertThat(ex.getCause(), not(nullValue()));
            assertThat(ex.getCause().getMessage(), containsString("Refusing to resolve entity"));
        }

    }


    @Issue("SECURITY-167")
    @Test
    public void testUpdateByXmlDoesNotFail() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<project>\n" +
                "  <description>&amp;</description>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "</project>";

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("security-167");
        project.updateByXml((Source) new StreamSource(new StringReader(xml)));
        assertThat(project.getDescription(), is("&")); // the entity is transformed
    }

}
