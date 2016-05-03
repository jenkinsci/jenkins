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

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import org.jvnet.hudson.test.Issue;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

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

    /**
     * Tests getValue() directly. Tests the parse methods too (indirectly - yeah, a purest would have
     * tests for each).
     */
    @Test
    public void testGetValue() throws XPathExpressionException, SAXException, IOException {
        URL configUrl = getClass().getResource("/jenkins/xml/config.xml");
        File configFile = new File(configUrl.getFile());

        Assert.assertEquals("1.480.1", XMLUtils.getValue("/hudson/version", configFile));
        Assert.assertEquals("", XMLUtils.getValue("/hudson/unknown-element", configFile));
    }
    
    @Test
    public void testParse_with_XXE() throws IOException, XPathExpressionException {
        try {
            final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE foo [\n" +
                    "   <!ELEMENT foo ANY >\n" +
                    "   <!ENTITY xxe SYSTEM \"http://abc.com/temp/test.jsp\" >]> " +
                    "<foo>&xxe;</foo>";

            StringReader stringReader = new StringReader(xml);
            Document doc = XMLUtils.parse(stringReader);
            Assert.fail("Expecting SAXException for XXE.");
        } catch (SAXException e) {
            assertThat(e.getMessage(), containsString("DOCTYPE is disallowed"));
        }
    }    
}
