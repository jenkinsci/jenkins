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

import static hudson.util.FormValidation.Kind.ERROR
import static hudson.util.FormValidation.Kind.WARNING
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertEquals

import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.util.FormValidation
import org.junit.Rule
import org.junit.Test
import org.apache.commons.io.IOUtils
import org.jvnet.hudson.test.GroovyJenkinsRule

/**
 * @author Kohsuke Kawaguchi
 */
class SlaveTest {

    @Rule
    public GroovyJenkinsRule j = new GroovyJenkinsRule()

    /**
     * Makes sure that a form validation method gets inherited.
     */
    @Test
    void formValidation() {
        j.executeOnServer {
            assertNotNull(j.jenkins.getDescriptor(DumbSlave).getCheckUrl("remoteFS"))
        }
    }

    /**
     * Programmatic config.xml submission.
     */
    @Test
    void slaveConfigDotXml() {
        DumbSlave s = j.createSlave()
        def wc = j.createWebClient()
        def p = wc.goTo("computer/${s.name}/config.xml", "application/xml")
        def xml = p.webResponse.contentAsString
        new XmlSlurper().parseText(xml)   // verify that it is XML

        // make sure it survives the roundtrip
        post("computer/${s.name}/config.xml",xml);

        assertNotNull(j.jenkins.getNode(s.name))

        xml = IOUtils.toString(getClass().getResource("SlaveTest/slave.xml").openStream());
        xml = xml.replace("NAME",s.name)
        post("computer/${s.name}/config.xml",xml);

        s = j.jenkins.getNode(s.name)
        assertNotNull(s)
        assertEquals("some text",s.nodeDescription)
        assertEquals(JNLPLauncher.class,s.launcher.class)
    }

    def post(url,String xml) {
        HttpURLConnection con = new URL(j.getURL(),url).openConnection();
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type","application/xml;charset=UTF-8")
        con.setRequestProperty(".crumb","test")
        con.doOutput = true;
        con.outputStream.write(xml.getBytes("UTF-8"))
        con.outputStream.close();
        IOUtils.copy(con.inputStream,System.out)
    }

    @Test
    void remoteFsCheck() {
        def d = j.jenkins.getDescriptorByType(DumbSlave.DescriptorImpl.class)
        assert d.doCheckRemoteFS("c:\\")==FormValidation.ok();
        assert d.doCheckRemoteFS("/tmp")==FormValidation.ok();
        assert d.doCheckRemoteFS("relative/path").kind==WARNING;
        assert d.doCheckRemoteFS("/net/foo/bar/zot").kind==WARNING;
        assert d.doCheckRemoteFS("\\\\machine\\folder\\foo").kind==WARNING;
    }
}
