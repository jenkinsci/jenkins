/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

package hudson.model.labels;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.XmlFile;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LabelAtomSecurity1986Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void nonexisting() {
        LabelAtom nonexistent = j.jenkins.getLabelAtom("nonexistent");
        XmlFile configFile = nonexistent.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    void normal() throws Exception {
        j.submit(j.createWebClient().goTo("labelAtom/foo/configure").getFormByName("config"));
        LabelAtom foo = j.jenkins.getLabelAtom("foo");
        XmlFile configFile = foo.getConfigFile();
        assertTrue(configFile.getFile().exists());
        assertThat(configFile.getFile().getParentFile().getName(), equalTo("labels"));
    }

    @Test
    @Issue("SECURITY-1986")
    void startsWithDoubleDotSlash() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/..%2ffoo/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("../foo");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    void startsWithSlash() throws Exception {
        // Not a great result, but it works and doesn't cause problems.
        j.submit(j.createWebClient().goTo("labelAtom/%2ffoo/configure").getFormByName("config"));
        LabelAtom foo = j.jenkins.getLabelAtom("/foo");
        XmlFile configFile = foo.getConfigFile();
        assertTrue(configFile.getFile().exists());
        assertThat(configFile.getFile().getParentFile().getName(), equalTo("labels"));
    }

    @Test
    void startsWithDoubleDot() throws Exception {
        j.submit(j.createWebClient().goTo("labelAtom/..foo/configure").getFormByName("config"));
        LabelAtom foo = j.jenkins.getLabelAtom("..foo");
        XmlFile configFile = foo.getConfigFile();
        assertTrue(configFile.getFile().exists());
        assertThat(configFile.getFile().getParentFile().getName(), equalTo("labels"));
    }

    @Test
    @Issue("SECURITY-1986")
    void endsWithDoubleDotSlash() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/foo..%2f/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("foo../");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    void endsWithDoubleDot() throws Exception {
        j.submit(j.createWebClient().goTo("labelAtom/foo../configure").getFormByName("config"));
        LabelAtom foo = j.jenkins.getLabelAtom("foo..");
        XmlFile configFile = foo.getConfigFile();
        assertTrue(configFile.getFile().exists());
        assertThat(configFile.getFile().getParentFile().getName(), equalTo("labels"));
    }

    @Test
    @Issue("SECURITY-1986")
    void startsWithDoubleDotBackslash() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/..\\foo/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("..\\foo");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    @Issue("SECURITY-1986")
    void endsWithDoubleDotBackslash() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/foo..\\/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("foo..\\");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    @Issue("SECURITY-1986")
    void middleDotsSlashes() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/foo%2f..%2fgoo/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("foo/../goo");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    @Issue("SECURITY-1986")
    void middleDotsBackslashes() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/foo%\\..\\goo/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom("foo\\..\\");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

    @Test
    @Issue("SECURITY-1986")
    void programmaticCreationInvalidName() {
        LabelAtom label = new LabelAtom("foo/../goo");
        assertThrows(IOException.class, label::save);
    }

    @Test
    void programmaticCreation() throws IOException {
        LabelAtom label = new LabelAtom("foo");
        label.save();
        LabelAtom foo = j.jenkins.getLabelAtom("foo");
        XmlFile configFile = foo.getConfigFile();
        assertTrue(configFile.getFile().exists());
        assertThat(configFile.getFile().getParentFile().getName(), equalTo("labels"));
    }

    @Test
    @Issue("SECURITY-1986")
    void startsWithTripleDotBackslash() {
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> j.submit(j.createWebClient().goTo("labelAtom/...%2ffoo/configure").getFormByName("config")), "Should have rejected label.");
        assertThat(e.getStatusCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        LabelAtom foo = j.jenkins.getLabelAtom(".../foo");
        XmlFile configFile = foo.getConfigFile();
        assertFalse(configFile.getFile().exists());
    }

}
