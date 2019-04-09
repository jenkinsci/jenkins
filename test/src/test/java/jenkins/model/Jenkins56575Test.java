/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
package jenkins.model;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.Launcher;
import hudson.Main;
import hudson.lifecycle.Lifecycle;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.util.StreamTaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

public class Jenkins56575Test {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    private File jar;
    
    private AtomicReference<Boolean> restartCalled = new AtomicReference<>(false);
    private AtomicReference<Boolean> verifyRestartableCalled = new AtomicReference<>(false);
    private Semaphore restartSemaphore = new Semaphore(0);
    
    private Lifecycle previousLifecycle;
    private Field instanceField;
    
    @Before
    public void setupMockLifecycle() throws Exception {
        Main.isUnitTest = false;
        j.jenkins.setCrumbIssuer(null);
        
        previousLifecycle = Lifecycle.get();
        
        instanceField = Lifecycle.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, new Lifecycle() {
            @Override
            public void verifyRestartable() throws RestartNotSupportedException {
                verifyRestartableCalled.set(true);
            }
            
            @Override
            public void restart() throws IOException, InterruptedException {
                restartCalled.set(true);
                restartSemaphore.release();
            }
        });
    }
    
    @Before
    public void grabCliJar() throws IOException {
        jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
    }
    
    @After
    public void resetSettings() throws Exception {
        Main.isUnitTest = true;
        
        instanceField.set(null, previousLifecycle);
    }
    
    @Test
    public void testRestart_regularClient() throws Exception {
        WebClient wc = j.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "restart"), HttpMethod.POST);
        
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        Page page = wc.getPage(request);
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_MOVED_TEMP));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    @Test
    @Issue("JENKINS-56575")
    public void testRestart_restClient() throws Exception {
        WebClient wc = j.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "restart"), HttpMethod.POST);
        request.setAdditionalHeader("Accept", "application/json");
        
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        Page page = wc.getPage(request);
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_OK));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    @Test
    public void testRestart_cliClient() throws Exception {
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        assertEquals(0, launchCLI("java",
                "-jar", jar.getAbsolutePath(),
                "-s", j.getURL().toString(),
                "restart"));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    @Test
    public void testSafeRestart_regularClient() throws Exception {
        WebClient wc = j.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "safeRestart"), HttpMethod.POST);
        
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        Page page = wc.getPage(request);
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_MOVED_TEMP));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    @Test
    @Issue("JENKINS-56575")
    public void testSafeRestart_restClient() throws Exception {
        WebClient wc = j.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);
        WebRequest request = new WebRequest(new URL(j.getURL() + "safeRestart"), HttpMethod.POST);
        request.setAdditionalHeader("Accept", "application/json");
        
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        Page page = wc.getPage(request);
        assertThat(page.getWebResponse().getStatusCode(), is(HttpURLConnection.HTTP_ACCEPTED));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    @Test
    public void testSafeRestart_cliClient() throws Exception {
        assertThat(verifyRestartableCalled.get(), is(false));
        assertThat(restartCalled.get(), is(false));
        
        assertEquals(0, launchCLI("java",
                "-jar", jar.getAbsolutePath(),
                "-s", j.getURL().toString(),
                "safe-restart"));
        
        restartSemaphore.acquire(1);
        
        assertThat(verifyRestartableCalled.get(), is(true));
        assertThat(restartCalled.get(), is(true));
    }
    
    private int launchCLI(String... cmdArgs) throws Exception {
        return new Launcher.LocalLauncher(StreamTaskListener.fromStderr())
                .launch()
                .cmds(cmdArgs)
                .join();
    }
}
