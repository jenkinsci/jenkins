/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.util.Secret;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class ProxyConfigurationManagerGUITest {

    @RegisterExtension
    public JenkinsSessionExtension rr = new JenkinsSessionExtension();

    @Test
    void configRoundtrip() throws Throwable {
        rr.then(r -> {
            assertNull(r.jenkins.proxy);
            r.jenkins.proxy = new ProxyConfiguration("proxy.mycorp", 80);
            r.configRoundtrip();
            FileUtils.copyFile(new File(r.jenkins.root, "proxy.xml"), System.out);
        });
        rr.then(r -> {
            ProxyConfiguration pc = r.jenkins.proxy;
            assertNotNull(pc);
            assertEquals("proxy.mycorp", pc.getName());
            assertEquals(80, pc.getPort());
            assertNull(pc.getUserName());
            pc.setUserName("proxyuser");
            pc.setSecretPassword(Secret.fromString("proxypass"));
            r.configRoundtrip();
            FileUtils.copyFile(new File(r.jenkins.root, "proxy.xml"), System.out);
        });
        rr.then(r -> {
            ProxyConfiguration pc = r.jenkins.proxy;
            assertEquals("proxy.mycorp", pc.getName());
            assertEquals(80, pc.getPort());
            assertEquals("proxyuser", pc.getUserName());
            assertEquals("proxypass", pc.getSecretPassword().getPlainText());
        });
    }

}
