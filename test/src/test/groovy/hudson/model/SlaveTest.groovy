/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model

import hudson.slaves.DumbSlave
import hudson.util.FormValidation
import org.jvnet.hudson.test.GroovyHudsonTestCase
import org.apache.commons.io.IOUtils
import hudson.slaves.JNLPLauncher

import static hudson.util.FormValidation.Kind.ERROR
import static hudson.util.FormValidation.Kind.WARNING

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveTest extends GroovyHudsonTestCase {
    /**
     * Makes sure that a form validation method gets inherited.
     */
    void testFormValidation() {
        executeOnServer {
            assertNotNull(jenkins.getDescriptor(DumbSlave.class).getCheckUrl("remoteFS"))
        }
    }

    /**
     * Programmatica config.xml submission.
     */
    void testSlaveConfigDotXml() {
        DumbSlave s = createSlave();
        def wc = createWebClient()
        def p = wc.goTo("/computer/${s.name}/config.xml","application/xml");
        def xml = p.webResponse.contentAsString
        new XmlSlurper().parseText(xml)   // verify that it is XML

        // make sure it survives the roundtrip
        post("/computer/${s.name}/config.xml",xml);

        assertNotNull(jenkins.getNode(s.name))

        xml = IOUtils.toString(getClass().getResource("SlaveTest/slave.xml").openStream());
        xml = xml.replace("NAME",s.name)
        post("/computer/${s.name}/config.xml",xml);

        s = jenkins.getNode(s.name)
        assertNotNull(s)
        assertEquals("some text",s.nodeDescription)
        assertEquals(JNLPLauncher.class,s.launcher.class)
    }
    
    def post(url,String xml) {
        HttpURLConnection con = new URL(this.getURL(),url).openConnection();
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type","application/xml;charset=UTF-8")
        con.setRequestProperty(".crumb","test")
        con.doOutput = true;
        con.outputStream.write(xml.getBytes("UTF-8"))
        con.outputStream.close();
        IOUtils.copy(con.inputStream,System.out)
    }

    public void testRemoteFsCheck() {
        def d = jenkins.getDescriptorByType(DumbSlave.DescriptorImpl.class)
        assert d.doCheckRemoteFS("c:\\")==FormValidation.ok();
        assert d.doCheckRemoteFS("/tmp")==FormValidation.ok();
        assert d.doCheckRemoteFS("relative/path").kind==ERROR;
        assert d.doCheckRemoteFS("/net/foo/bar/zot").kind==WARNING;
        assert d.doCheckRemoteFS("\\\\machine\\folder\\foo").kind==WARNING;
    }
}
