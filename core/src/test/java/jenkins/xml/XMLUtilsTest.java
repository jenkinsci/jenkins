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

package jenkins.xml;

import jenkins.util.xml.XMLUtils;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import org.jvnet.hudson.test.Issue;

public class XMLUtilsTest {

    @Issue("SECURITY-167")
    @Test()
    public void testSafeTransformDoesNotProcessForeignResources() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[\n" +
                "  <!ENTITY foo SYSTEM \"file:///\">\n" +
                "]>\n" +
                "<project>\n" +
                "  <actions/>\n" +
                "  <description>&foo;</description>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>true</canRoam>\n" +
                "  <triggers/>\n" +
                "  <builders/>\n" +
                "  <publishers/>\n" +
                "  <buildWrappers/>\n" +
                "</project>";


        StringWriter stringWriter = new StringWriter();
        try {
            XMLUtils.safeTransform(new StreamSource(new StringReader(xml)), new StreamResult(stringWriter));
            // if no exception then JAXP is swallowing these - so there should be no entity in the description.
            assertThat(stringWriter.toString(), containsString("<description/>"));
        } catch (TransformerException ex) {
            assertThat(ex.getMessage(), containsString("Refusing to resolve entity"));
        }

    }


    @Issue("SECURITY-167")
    @Test()
    public void testUpdateByXmlIDoesNotFail() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<project>\n" +
                "  <actions/>\n" +
                "  <description>&amp;</description>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>true</canRoam>\n" +
                "  <triggers/>\n" +
                "  <builders/>\n" +
                "  <publishers/>\n" +
                "  <buildWrappers/>\n" +
                "</project>";

        StringWriter stringWriter = new StringWriter();

        XMLUtils.safeTransform(new StreamSource(new StringReader(xml)), new StreamResult(stringWriter));
        // make sure that normal entities are retained.
        assertThat(stringWriter.toString(), containsString("<description>&amp;</description>"));
    }

}
