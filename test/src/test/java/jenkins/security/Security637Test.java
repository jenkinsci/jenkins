/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class Security637Test {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    @Issue("SECURITY-637")
    void urlSafeDeserialization_handler_inSameJVMRemotingContext() throws Throwable {
        sessions.then(j -> {
                DumbSlave slave = j.createOnlineSlave(null, new EnvVars("JAVA_TOOL_OPTIONS", "--add-opens=java.base/java.net=ALL-UNNAMED"));
                String unsafeHandlerClassName = slave.getChannel().call(new URLHandlerCallable(new URL("https://www.google.com/")));
                assertThat(unsafeHandlerClassName, containsString("SafeURLStreamHandler"));

                String safeHandlerClassName = slave.getChannel().call(new URLHandlerCallable(new URL("file", null, -1, "", null)));
                assertThat(safeHandlerClassName, not(containsString("SafeURLStreamHandler")));
        });
    }

    private static class URLHandlerCallable extends MasterToSlaveCallable<String, Exception> {
        private URL url;

        URLHandlerCallable(URL url) {
            this.url = url;
        }

        @Override
        public String call() throws Exception {
            Field handlerField = URL.class.getDeclaredField("handler");
            handlerField.setAccessible(true);
            URLStreamHandler handler = (URLStreamHandler) handlerField.get(url);
            return handler.getClass().getName();
        }
    }

    @Disabled("TODO these map to different IPs now")
    @Test
    @Issue("SECURITY-637")
    void urlDnsEquivalence() throws Throwable {
        sessions.then(j ->
                // due to the DNS resolution they are equal
                assertEquals(
                        new URI("https://jenkins.io").toURL(),
                        new URI("https://www.jenkins.io").toURL()
                ));
    }

    @Disabled("TODO these map to different IPs now")
    @Test
    @Issue("SECURITY-637")
    void urlSafeDeserialization_urlBuiltInAgent_inSameJVMRemotingContext() throws Throwable {
        sessions.then(j -> {
                DumbSlave slave = j.createOnlineSlave();

                // we bypass the standard equals method that resolve the hostname
                assertThat(
                        slave.getChannel().call(new URLBuilderCallable("https://jenkins.io")),
                        not(equalTo(
                                slave.getChannel().call(new URLBuilderCallable("https://www.jenkins.io"))
                        ))
                );
        });
    }

    private static class URLBuilderCallable extends MasterToSlaveCallable<URL, Exception> {
        private String url;

        URLBuilderCallable(String url) {
            this.url = url;
        }

        @Override
        public URL call() throws Exception {
            return new URI(url).toURL();
        }
    }

    @Disabled("TODO these map to different IPs now")
    @Test
    @Issue("SECURITY-637")
    void urlSafeDeserialization_urlBuiltInMaster_inSameJVMRemotingContext() throws Throwable {
        sessions.then(j -> {
                DumbSlave slave = j.createOnlineSlave();

                // we bypass the standard equals method that resolve the hostname
                assertThat(
                        slave.getChannel().call(new URLTransferCallable(new URL("https://jenkins.io"))),
                        not(equalTo(
                                slave.getChannel().call(new URLTransferCallable(new URL("https://www.jenkins.io")))
                        ))
                );

                // due to the DNS resolution they are equal
                assertEquals(
                        new URI("https://jenkins.io").toURL(),
                        new URI("https://www.jenkins.io").toURL()
                );
        });
    }

    // the URL is serialized / deserialized twice, master => agent and then agent => master
    private static class URLTransferCallable extends MasterToSlaveCallable<URL, Exception> {
        private URL url;

        URLTransferCallable(URL url) {
            this.url = url;
        }

        @Override
        public URL call() throws Exception {
            return url;
        }
    }

    @Test
    @Issue("SECURITY-637")
    void urlSafeDeserialization_inXStreamContext() throws Throwable {
        sessions.then(j -> {
                FreeStyleProject project = j.createFreeStyleProject("project-with-url");
                URLJobProperty URLJobProperty = new URLJobProperty(
                        // url to be wrapped
                        new URI("https://www.google.com/").toURL(),
                        // safe url, not required to be wrapped
                        new URL("https", null, -1, "", null)
                );
                project.addProperty(URLJobProperty);

                project.save();
        });

        sessions.then(j -> {
                FreeStyleProject project = j.jenkins.getItemByFullName("project-with-url", FreeStyleProject.class);
                assertNotNull(project);

                Field handlerField = URL.class.getDeclaredField("handler");
                try {
                    handlerField.setAccessible(true);
                } catch (RuntimeException e) {
                    assumeTrue(false, e.getMessage());
                }

                URLJobProperty urlJobProperty = project.getProperty(URLJobProperty.class);
                for (URL url : urlJobProperty.urlSet) {
                    URLStreamHandler handler = (URLStreamHandler) handlerField.get(url);
                    if (url.getHost() == null || url.getHost().isEmpty()) {
                        assertThat(handler.getClass().getName(), not(containsString("SafeURLStreamHandler")));
                    } else {
                        assertThat(handler.getClass().getName(), containsString("SafeURLStreamHandler"));
                    }
                }
        });
    }

    public static class URLJobProperty extends JobProperty<FreeStyleProject> {

        private Set<URL> urlSet;

        @SuppressWarnings(value = "checkstyle:redundantmodifier")
        public URLJobProperty(URL... urls) {
            this.urlSet = new HashSet<>();
            Collections.addAll(urlSet, urls);
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            return true;
        }

        @TestExtension("urlSafeDeserialization_inXStreamContext")
        public static class DescriptorImpl extends JobPropertyDescriptor {}
    }
}
