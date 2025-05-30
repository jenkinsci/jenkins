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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;
import jenkins.util.xml.XMLUtils;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.xml.sax.SAXException;

class XMLUtilsTest {

    @Issue("SECURITY-167")
    @Test
    void testSafeTransformDoesNotProcessForeignResources() throws Exception {
        final String xml = """
                <?xml version='1.0' encoding='UTF-8'?>
                <!DOCTYPE project[
                  <!ENTITY foo SYSTEM "file:///">
                ]>
                <project>
                  <actions/>
                  <description>&foo;</description>
                  <keepDependencies>false</keepDependencies>
                  <properties/>
                  <scm class="hudson.scm.NullSCM"/>
                  <canRoam>true</canRoam>
                  <triggers/>
                  <builders/>
                  <publishers/>
                  <buildWrappers/>
                </project>""";


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
    @Test
    void testUpdateByXmlIDoesNotFail() throws Exception {
        final String xml = """
                <?xml version='1.0' encoding='UTF-8'?>
                <project>
                  <actions/>
                  <description>&amp;</description>
                  <keepDependencies>false</keepDependencies>
                  <properties/>
                  <scm class="hudson.scm.NullSCM"/>
                  <canRoam>true</canRoam>
                  <triggers/>
                  <builders/>
                  <publishers/>
                  <buildWrappers/>
                </project>""";

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
    void testGetValue() throws XPathExpressionException, SAXException, IOException {
        URL configUrl = getClass().getResource("/jenkins/xml/config.xml");
        File configFile = new File(configUrl.getFile());

        assertEquals("1.480.1", XMLUtils.getValue("/hudson/version", configFile));
        assertEquals("", XMLUtils.getValue("/hudson/unknown-element", configFile));
    }

    @Test
    void testParse_with_XXE() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                   <!ELEMENT foo ANY >
                   <!ENTITY xxe SYSTEM "http://abc.com/temp/test.jsp" >]> \
                <foo>&xxe;</foo>""";

        StringReader stringReader = new StringReader(xml);
        final SAXException e = assertThrows(SAXException.class, () -> XMLUtils.parse(stringReader));
        assertThat(e.getMessage(), containsString("\"http://apache.org/xml/features/disallow-doctype-decl\""));
    }
}
