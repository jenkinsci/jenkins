/*
 * The MIT License
 *
 * Copyright 2011, OHTAKE Tomohiro.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.util.VersionNumber;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.MockedStatic;

public class FunctionsTest {
    @Test
    public void testGetActionUrl_absoluteUriWithAuthority() {
        String[] uris = {
            "http://example.com/foo/bar",
            "https://example.com/foo/bar",
            "ftp://example.com/foo/bar",
            "svn+ssh://nobody@example.com/foo/bar",
        };
        for (String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    @Test
    @Issue("JENKINS-7725")
    public void testGetActionUrl_absoluteUriWithoutAuthority() {
        String[] uris = {
            "mailto:nobody@example.com",
            "mailto:nobody@example.com?subject=hello",
            "javascript:alert('hello')",
        };
        for (String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    @Test
    public void testGetActionUrl_absolutePath() {
        String contextPath = "/jenkins";
        StaplerRequest2 req = createMockRequest(contextPath);
        String[] paths = {
            "/",
            "/foo/bar",
        };

        try (MockedStatic<Stapler> mocked = mockStatic(Stapler.class)) {
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            for (String path : paths) {
                String result = Functions.getActionUrl(null, createMockAction(path));
                assertEquals(contextPath + path, result);
            }
        }
    }

    @Test
    public void testGetActionUrl_relativePath() {
        String contextPath = "/jenkins";
        String itUrl = "iturl/";
        StaplerRequest2 req = createMockRequest(contextPath);
        String[] paths = {
            "foo/bar",
            "./foo/bar",
            "../foo/bar",
        };
        try (MockedStatic<Stapler> mocked = mockStatic(Stapler.class)) {
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            for (String path : paths) {
                String result = Functions.getActionUrl(itUrl, createMockAction(path));
                assertEquals(contextPath + "/" + itUrl + path, result);
            }
        }
    }

    @Test
    public void testGetRelativeLinkTo_JobContainedInView() {
        String contextPath = "/jenkins";
        StaplerRequest2 req = createMockRequest(contextPath);
        try (
                MockedStatic<Stapler> mocked = mockStatic(Stapler.class);
                MockedStatic<Jenkins> mockedJenkins = mockStatic(Jenkins.class)
        ) {
            Jenkins j = createMockJenkins(mockedJenkins);
            ItemGroup parent = j;
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            View view = mock(View.class);
            when(view.getOwner()).thenReturn(j);
            when(j.getItemGroup()).thenReturn(j);
            createMockAncestors(req, createAncestor(view, "."), createAncestor(j, "../.."));
            TopLevelItem i = createMockItem(parent, "job/i/");
            when(view.getItems()).thenReturn(List.of(i));
            String result = Functions.getRelativeLinkTo(i);
            assertEquals("job/i/", result);
        }
    }

    @Test
    public void testGetRelativeLinkTo_JobFromComputer() {
        String contextPath = "/jenkins";
        StaplerRequest2 req = createMockRequest(contextPath);
        try (
                MockedStatic<Stapler> mocked = mockStatic(Stapler.class);
                MockedStatic<Jenkins> mockedJenkins = mockStatic(Jenkins.class)
        ) {
            Jenkins j = createMockJenkins(mockedJenkins);
            ItemGroup parent = j;
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            Computer computer = mock(Computer.class);
            createMockAncestors(req, createAncestor(computer, "."), createAncestor(j, "../.."));
            TopLevelItem i = createMockItem(parent, "job/i/");
            String result = Functions.getRelativeLinkTo(i);
            assertEquals("/jenkins/job/i/", result);
        }
    }

    @Ignore("too expensive to make it correct")
    @Test
    public void testGetRelativeLinkTo_JobNotContainedInView() {
        String contextPath = "/jenkins";
        StaplerRequest2 req = createMockRequest(contextPath);
        try (
                MockedStatic<Stapler> mocked = mockStatic(Stapler.class);
                MockedStatic<Jenkins> mockedJenkins = mockStatic(Jenkins.class)
        ) {
            Jenkins j = createMockJenkins(mockedJenkins);
            ItemGroup parent = j;
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            View view = mock(View.class);
            when(view.getOwner().getItemGroup()).thenReturn(parent);
            createMockAncestors(req, createAncestor(j, "../.."), createAncestor(view, "."));
            TopLevelItem i = createMockItem(parent, "job/i/");
            when(view.getItems()).thenReturn(Collections.emptyList());
            String result = Functions.getRelativeLinkTo(i);
            assertEquals("/jenkins/job/i/", result);
        }
    }

    private interface TopLevelItemAndItemGroup<T extends TopLevelItem> extends TopLevelItem, ItemGroup<T>, ViewGroup {}

    @Test
    public void testGetRelativeLinkTo_JobContainedInViewWithinItemGroup() {
        String contextPath = "/jenkins";
        StaplerRequest2 req = createMockRequest(contextPath);
        try (
                MockedStatic<Stapler> mocked = mockStatic(Stapler.class);
                MockedStatic<Jenkins> mockedJenkins = mockStatic(Jenkins.class)
        ) {
            Jenkins j = createMockJenkins(mockedJenkins);
            TopLevelItemAndItemGroup parent = mock(TopLevelItemAndItemGroup.class);
            when(parent.getShortUrl()).thenReturn("parent/");
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            View view = mock(View.class);
            when(view.getOwner()).thenReturn(parent);
            when(parent.getItemGroup()).thenReturn(parent);
            createMockAncestors(req, createAncestor(j, "../../.."), createAncestor(parent, "../.."), createAncestor(view, "."));
            TopLevelItem i = createMockItem(parent, "job/i/", "parent/job/i/");
            when(view.getItems()).thenReturn(List.of(i));
            String result = Functions.getRelativeLinkTo(i);
            assertEquals("job/i/", result);
        }
    }

    @Issue("JENKINS-17713")
    @Test public void getRelativeLinkTo_MavenModules() {
        StaplerRequest2 req = createMockRequest("/jenkins");
        try (
                MockedStatic<Stapler> mocked = mockStatic(Stapler.class);
                MockedStatic<Jenkins> mockedJenkins = mockStatic(Jenkins.class)
        ) {
            Jenkins j = createMockJenkins(mockedJenkins);
            mocked.when(Stapler::getCurrentRequest2).thenReturn(req);
            TopLevelItemAndItemGroup ms = mock(TopLevelItemAndItemGroup.class);
            when(ms.getShortUrl()).thenReturn("job/ms/");
            // TODO "." (in second ancestor) is what Stapler currently fails to do. Could edit test to use ".." but set a different request path?
            createMockAncestors(req, createAncestor(j, "../.."), createAncestor(ms, "."));
            Item m = mock(Item.class);
            when(m.getParent()).thenReturn(ms);
            when(m.getShortUrl()).thenReturn("grp$art/");
            assertEquals("grp$art/", Functions.getRelativeLinkTo(m));
        }
    }

    @Test
    public void testGetRelativeDisplayName() {
        Item i = mock(Item.class);
        when(i.getName()).thenReturn("jobName");
        when(i.getFullDisplayName()).thenReturn("displayName");
        assertEquals("displayName", Functions.getRelativeDisplayNameFrom(i, null));
    }

    @Test
    public void testGetRelativeDisplayNameInsideItemGroup() {
        Item i = mock(Item.class);
        when(i.getName()).thenReturn("jobName");
        when(i.getDisplayName()).thenReturn("displayName");
        TopLevelItemAndItemGroup ig = mock(TopLevelItemAndItemGroup.class);
        ItemGroup j = mock(Jenkins.class);
        when(ig.getName()).thenReturn("parent");
        when(ig.getDisplayName()).thenReturn("parentDisplay");
        when(ig.getParent()).thenReturn(j);
        when(i.getParent()).thenReturn(ig);
        Item i2 = mock(Item.class);
        when(i2.getDisplayName()).thenReturn("top");
        when(i2.getParent()).thenReturn(j);

        assertEquals("displayName", Functions.getRelativeDisplayNameFrom(i, ig));
        assertEquals("parentDisplay » displayName", Functions.getRelativeDisplayNameFrom(i, j));
        assertEquals(".. » top", Functions.getRelativeDisplayNameFrom(i2, ig));
    }

    private void createMockAncestors(StaplerRequest2 req, Ancestor... ancestors) {
        List<Ancestor> ancestorsList = Arrays.asList(ancestors);
        when(req.getAncestors()).thenReturn(ancestorsList);
    }

    private TopLevelItem createMockItem(ItemGroup p, String shortUrl) {
        return createMockItem(p, shortUrl, shortUrl);
    }

    private TopLevelItem createMockItem(ItemGroup p, String shortUrl, String url) {
        TopLevelItem i = mock(TopLevelItem.class);
        when(i.getShortUrl()).thenReturn(shortUrl);
        when(i.getUrl()).thenReturn(url);
        when(i.getParent()).thenReturn(p);
        return i;
    }

    private Jenkins createMockJenkins(MockedStatic<Jenkins> mockedJenkins) {
        Jenkins j = mock(Jenkins.class);
        mockedJenkins.when(Jenkins::get).thenReturn(j);
        return j;
    }

    private static Ancestor createAncestor(Object o, String relativePath) {
        Ancestor a = mock(Ancestor.class);
        when(a.getObject()).thenReturn(o);
        when(a.getRelativePath()).thenReturn(relativePath);
        return a;
    }

    @Test
    public void testGetActionUrl_unparseable() {
        assertNull(Functions.getActionUrl(null, createMockAction("http://example.net/stuff?something=^woohoo")));
    }

    private static Action createMockAction(String uri) {
        Action action = mock(Action.class);
        when(action.getUrlName()).thenReturn(uri);
        return action;
    }

    private static StaplerRequest2 createMockRequest(String contextPath) {
        StaplerRequest2 req = mock(StaplerRequest2.class);
        when(req.getContextPath()).thenReturn(contextPath);
        return req;
    }

    @Test
    @Issue("JENKINS-16630")
    public void testHumanReadableFileSize() {
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            assertEquals("0 B", Functions.humanReadableByteSize(0));
            assertEquals("1023 B", Functions.humanReadableByteSize(1023));
            assertEquals("1.00 KiB", Functions.humanReadableByteSize(1024));
            assertEquals("1.50 KiB", Functions.humanReadableByteSize(1536));
            assertEquals("20.00 KiB", Functions.humanReadableByteSize(20480));
            assertEquals("1023.00 KiB", Functions.humanReadableByteSize(1047552));
            assertEquals("1.00 MiB", Functions.humanReadableByteSize(1048576));
            assertEquals("1.50 GiB", Functions.humanReadableByteSize(1610612700));
            assertEquals("1.50 TiB", Functions.humanReadableByteSize(1649267441664L));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Issue("JENKINS-17030")
    @Test
    public void testBreakableString() {

        assertBrokenAs("Hello world!", "Hello world!");
        assertBrokenAs("Hello-world!", "Hello", "-world!");
        assertBrokenAs("ALongStringThatCanNotBeBrokenByDefaultAndNeedsToUseTheBreakableElement",
                "ALongStringThatCanNo", "tBeBrokenByDefaultAn", "dNeedsToUseTheBreaka", "bleElement");
        assertBrokenAs("DontBreakShortStringBefore-Hyphen", "DontBreakShortStringBefore", "-Hyphen");
        assertBrokenAs("jenkins_main_trunk", "jenkins", "_main", "_trunk");

        assertBrokenAs("&lt;&lt;&lt;&lt;&lt;", "", "&lt;", "&lt;", "&lt;", "&lt;", "&lt;");
        assertBrokenAs("&amp;&amp;&amp;&amp;&amp;", "", "&amp;", "&amp;", "&amp;", "&amp;", "&amp;");
        assertBrokenAs("&thetasym;&thetasym;&thetasym;", "", "&thetasym;", "&thetasym;", "&thetasym;");
        assertBrokenAs("Crazy &lt;ha ha&gt;", "Crazy ", "&lt;ha ha", "&gt;");
        assertBrokenAs("A;String>Full]Of)Weird}Punctuation", "A;String", ">Full", "]Of", ")Weird", "}Punctuation");
        assertBrokenAs("&lt;&lt;a&lt;bc&lt;def&lt;ghi&lt;", "", "&lt;", "&lt;a", "&lt;bc", "&lt;def", "&lt;ghi", "&lt;");
        assertBrokenAs("H,e.l/l:o=w_o+|d", "H", ",e", ".l", "/l", ":o", "=w", "_o", "+|d");
        assertBrokenAs("a¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷ", "a¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷa¶‱ﻷ", "a¶‱ﻷa¶‱ﻷa¶‱ﻷ");
        assertNull(Functions.breakableString(null));
    }

    private void assertBrokenAs(String plain, String... chunks) {
        assertEquals(
                String.join("<wbr>", chunks),
                Functions.breakableString(plain)
        );
    }

    @Issue("JENKINS-20800")
    @Test public void printLogRecordHtml() {
        LogRecord lr = new LogRecord(Level.INFO, "Bad input <xml/>");
        lr.setLoggerName("test");
        assertEquals("Bad input &lt;xml/&gt;\n", Functions.printLogRecordHtml(lr, null)[3]);
    }

    @Test public void printLogRecordHtmlNoLogger() {
        LogRecord lr = new LogRecord(Level.INFO, "<discarded/>");
        assertEquals("&lt;discarded/&gt;\n", Functions.printLogRecordHtml(lr, null)[3]);
    }

    @Test
    public void extractPluginNameFromIconSrcHandlesNull() {
        String result = Functions.extractPluginNameFromIconSrc(null);

        assertThat(result, is(emptyString()));
    }

    @Test
    public void extractPluginNameFromIconSrcHandlesEmptyString() {
        String result = Functions.extractPluginNameFromIconSrc("");

        assertThat(result, is(emptyString()));
    }

    @Test
    public void extractPluginNameFromIconSrcOnlyReturnsPluginFromStart() {
        String result = Functions.extractPluginNameFromIconSrc("symbol-plugin-mailer plugin-design-library");

        assertThat(result, is(equalTo("design-library")));
    }

    @Test
    public void extractPluginNameFromIconSrcExtractsPlugin() {
        String result = Functions.extractPluginNameFromIconSrc("symbol-padlock plugin-design-library");

        assertThat(result, is(equalTo("design-library")));
    }

    @Test
    public void extractPluginNameFromIconSrcWhichContainsPluginWordInThePluginName() {
        String result = Functions.extractPluginNameFromIconSrc("symbol-padlock plugin-design-library-plugin");

        assertThat(result, is(equalTo("design-library-plugin")));
    }

    @Issue("JDK-6507809")
    @Test public void printThrowable() {
        // Basics: a single exception. No change.
        assertPrintThrowable(new Stack("java.lang.NullPointerException: oops", "p.C.method1:17", "m.Main.main:1"),
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\tat m.Main.main(Main.java:1)\n",
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\tat m.Main.main(Main.java:1)\n");
        // try {…} catch (Exception x) {throw new IllegalStateException(x);}
        assertPrintThrowable(new Stack("java.lang.IllegalStateException: java.lang.NullPointerException: oops", "p.C.method1:19", "m.Main.main:1").
                       cause(new Stack("java.lang.NullPointerException: oops", "p.C.method2:23", "p.C.method1:17", "m.Main.main:1")),
            "java.lang.IllegalStateException: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n" +
            "Caused by: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\t... 1 more\n",
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "Caused: java.lang.IllegalStateException\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n");
        // try {…} catch (Exception x) {throw new IllegalStateException("more info");}
        assertPrintThrowable(new Stack("java.lang.IllegalStateException: more info", "p.C.method1:19", "m.Main.main:1").
                       cause(new Stack("java.lang.NullPointerException: oops", "p.C.method2:23", "p.C.method1:17", "m.Main.main:1")),
            "java.lang.IllegalStateException: more info\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n" +
            "Caused by: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\t... 1 more\n",
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "Caused: java.lang.IllegalStateException: more info\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n");
        // try {…} catch (Exception x) {throw new IllegalStateException("more info: " + x);}
        assertPrintThrowable(new Stack("java.lang.IllegalStateException: more info: java.lang.NullPointerException: oops", "p.C.method1:19", "m.Main.main:1").
                       cause(new Stack("java.lang.NullPointerException: oops", "p.C.method2:23", "p.C.method1:17", "m.Main.main:1")),
            "java.lang.IllegalStateException: more info: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n" +
            "Caused by: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\t... 1 more\n",
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "Caused: java.lang.IllegalStateException: more info\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n");
        // Synthetic stack showing an exception made elsewhere, such as happens with hudson.remoting.Channel.attachCallSiteStackTrace.
        Throwable t = new Stack("remote.Exception: oops", "remote.Place.method:17", "remote.Service.run:9");
        StackTraceElement[] callSite = new Stack("wrapped.Exception", "local.Side.call:11", "local.Main.main:1").getStackTrace();
        StackTraceElement[] original = t.getStackTrace();
        StackTraceElement[] combined = new StackTraceElement[original.length + 1 + callSite.length];
        System.arraycopy(original, 0, combined, 0, original.length);
        combined[original.length] = new StackTraceElement(".....", "remote call", null, -2);
        System.arraycopy(callSite, 0, combined, original.length + 1, callSite.length);
        t.setStackTrace(combined);
        assertPrintThrowable(t,
            "remote.Exception: oops\n" +
            "\tat remote.Place.method(Place.java:17)\n" +
            "\tat remote.Service.run(Service.java:9)\n" +
            "\tat ......remote call(Native Method)\n" +
            "\tat local.Side.call(Side.java:11)\n" +
            "\tat local.Main.main(Main.java:1)\n",
            "remote.Exception: oops\n" +
            "\tat remote.Place.method(Place.java:17)\n" +
            "\tat remote.Service.run(Service.java:9)\n" +
            "\tat ......remote call(Native Method)\n" +
            "\tat local.Side.call(Side.java:11)\n" +
            "\tat local.Main.main(Main.java:1)\n");
        // Same but now using a cause on the remote side.
        t = new Stack("remote.Wrapper: remote.Exception: oops", "remote.Place.method2:19", "remote.Service.run:9").cause(new Stack("remote.Exception: oops", "remote.Place.method1:11", "remote.Place.method2:17", "remote.Service.run:9"));
        callSite = new Stack("wrapped.Exception", "local.Side.call:11", "local.Main.main:1").getStackTrace();
        original = t.getStackTrace();
        combined = new StackTraceElement[original.length + 1 + callSite.length];
        System.arraycopy(original, 0, combined, 0, original.length);
        combined[original.length] = new StackTraceElement(".....", "remote call", null, -2);
        System.arraycopy(callSite, 0, combined, original.length + 1, callSite.length);
        t.setStackTrace(combined);
        assertPrintThrowable(t,
            "remote.Wrapper: remote.Exception: oops\n" +
            "\tat remote.Place.method2(Place.java:19)\n" +
            "\tat remote.Service.run(Service.java:9)\n" +
            "\tat ......remote call(Native Method)\n" +
            "\tat local.Side.call(Side.java:11)\n" +
            "\tat local.Main.main(Main.java:1)\n" +
            "Caused by: remote.Exception: oops\n" +
            "\tat remote.Place.method1(Place.java:11)\n" +
            "\tat remote.Place.method2(Place.java:17)\n" +
            "\tat remote.Service.run(Service.java:9)\n",
            "remote.Exception: oops\n" +
            "\tat remote.Place.method1(Place.java:11)\n" +
            "\tat remote.Place.method2(Place.java:17)\n" +
            "\tat remote.Service.run(Service.java:9)\n" + // we do not know how to elide the common part in this case
            "Caused: remote.Wrapper\n" +
            "\tat remote.Place.method2(Place.java:19)\n" +
            "\tat remote.Service.run(Service.java:9)\n" +
            "\tat ......remote call(Native Method)\n" +
            "\tat local.Side.call(Side.java:11)\n" +
            "\tat local.Main.main(Main.java:1)\n");
        // Suppressed exceptions:
        assertPrintThrowable(new Stack("java.lang.IllegalStateException: java.lang.NullPointerException: oops", "p.C.method1:19", "m.Main.main:1").
                       cause(new Stack("java.lang.NullPointerException: oops", "p.C.method2:23", "p.C.method1:17", "m.Main.main:1")).
                  suppressed(new Stack("java.io.IOException: could not close", "p.C.close:99", "p.C.method1:18", "m.Main.main:1"),
                             new Stack("java.io.IOException: java.lang.NullPointerException", "p.C.flush:77", "p.C.method1:18", "m.Main.main:1").
                       cause(new Stack("java.lang.NullPointerException", "p.C.findFlushee:70", "p.C.flush:75", "p.C.method1:18", "m.Main.main:1"))),
            "java.lang.IllegalStateException: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n" +
            "\tSuppressed: java.io.IOException: could not close\n" +
            "\t\tat p.C.close(C.java:99)\n" +
            "\t\tat p.C.method1(C.java:18)\n" +
            "\t\t... 1 more\n" +
            "\tSuppressed: java.io.IOException: java.lang.NullPointerException\n" +
            "\t\tat p.C.flush(C.java:77)\n" +
            "\t\tat p.C.method1(C.java:18)\n" +
            "\t\t... 1 more\n" +
            "\tCaused by: java.lang.NullPointerException\n" +
            "\t\tat p.C.findFlushee(C.java:70)\n" +
            "\t\tat p.C.flush(C.java:75)\n" +
            "\t\t... 2 more\n" +
            "Caused by: java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "\t... 1 more\n",
            "java.lang.NullPointerException: oops\n" +
            "\tat p.C.method2(C.java:23)\n" +
            "\tat p.C.method1(C.java:17)\n" +
            "Also:   java.io.IOException: could not close\n" +
            "\t\tat p.C.close(C.java:99)\n" +
            "\t\tat p.C.method1(C.java:18)\n" +
            "Also:   java.lang.NullPointerException\n" +
            "\t\tat p.C.findFlushee(C.java:70)\n" +
            "\t\tat p.C.flush(C.java:75)\n" +
            "\tCaused: java.io.IOException\n" +
            "\t\tat p.C.flush(C.java:77)\n" +
            "\t\tat p.C.method1(C.java:18)\n" +
            "Caused: java.lang.IllegalStateException\n" +
            "\tat p.C.method1(C.java:19)\n" +
            "\tat m.Main.main(Main.java:1)\n");
        // Custom printStackTrace implementations:
        assertPrintThrowable(new Throwable() {
            @Override
            public void printStackTrace(PrintWriter s) {
                s.println("Some custom exception");
            }
        }, "Some custom exception\n", "Some custom exception\n");
        // Circular references:
        Stack stack1 = new Stack("p.Exc1", "p.C.method1:17");
        Stack stack2 = new Stack("p.Exc2", "p.C.method2:27");
        stack1.cause(stack2);
        stack2.cause(stack1);
        //Format changed in 11.0.9 / 8.0.272 (JDK-8226809 / JDK-8252444 / JDK-8252489)

        if (getVersion().isNewerThanOrEqualTo(new VersionNumber("11.0.9")) ||
                (getVersion().getDigitAt(0) == 8 && getVersion().isNewerThanOrEqualTo(new VersionNumber("8.0.272")))) {
            assertPrintThrowable(stack1,
                    "p.Exc1\n" +
                            "\tat p.C.method1(C.java:17)\n" +
                            "Caused by: p.Exc2\n" +
                            "\tat p.C.method2(C.java:27)\n" +
                            "Caused by: [CIRCULAR REFERENCE: p.Exc1]\n",
                    "<cycle to p.Exc1>\n" +
                            "Caused: p.Exc2\n" +
                            "\tat p.C.method2(C.java:27)\n" +
                            "Caused: p.Exc1\n" +
                            "\tat p.C.method1(C.java:17)\n");
        } else {
            assertPrintThrowable(stack1,
                    "p.Exc1\n" +
                            "\tat p.C.method1(C.java:17)\n" +
                            "Caused by: p.Exc2\n" +
                            "\tat p.C.method2(C.java:27)\n" +
                            "\t[CIRCULAR REFERENCE:p.Exc1]\n",
                    "<cycle to p.Exc1>\n" +
                            "Caused: p.Exc2\n" +
                            "\tat p.C.method2(C.java:27)\n" +
                            "Caused: p.Exc1\n" +
                            "\tat p.C.method1(C.java:17)\n");
        }
    }

    private static VersionNumber getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2).replace("_", ".");
        }
        return new VersionNumber(version);
    }

    private static void assertPrintThrowable(Throwable t, String traditional, String custom) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        assertThat(sw.toString().replace(System.lineSeparator(), "\n"), is(traditional));
        String actual = Functions.printThrowable(t);
        System.out.println(actual);
        assertThat(actual.replace(System.lineSeparator(), "\n"), is(custom));
    }

    private static final class Stack extends Throwable {
        private static final Pattern LINE = Pattern.compile("(.+)[.](.+)[.](.+):(\\d+)");
        private final String toString;

        Stack(String toString, String... stack) {
            this.toString = toString;
            StackTraceElement[] lines = new StackTraceElement[stack.length];
            for (int i = 0; i < stack.length; i++) {
                Matcher m = LINE.matcher(stack[i]);
                assertTrue(m.matches());
                lines[i] = new StackTraceElement(m.group(1) + "." + m.group(2), m.group(3), m.group(2) + ".java", Integer.parseInt(m.group(4)));
            }
            setStackTrace(lines);
        }

        @Override
        public String toString() {
            return toString;
        }

        synchronized Stack cause(Throwable cause) {
            return (Stack) initCause(cause);
        }

        synchronized Stack suppressed(Throwable... suppressed) {
            for (Throwable t : suppressed) {
                addSuppressed(t);
            }
            return this;
        }
    }

    @Test
    public void tryGetIcon_shouldReturnNullForNull() throws Exception {
        assertThat(Functions.tryGetIcon(null), is(nullValue()));
    }

    @Test
    public void tryGetIcon_shouldReturnNullForSymbol() throws Exception {
        assertThat(Functions.tryGetIcon("symbol-search"), is(nullValue()));
    }

    @Test
    public void tryGetIcon_shouldReturnMetadataForExactSpec() throws Exception {
        assertThat(Functions.tryGetIcon("icon-help icon-sm"), is(not(nullValue())));
    }

    @Test
    public void tryGetIcon_shouldReturnMetadataForExtraSpec() throws Exception {
        assertThat(Functions.tryGetIcon("icon-help icon-sm extra-class"), is(not(nullValue())));
    }

    @Test
    public void tryGetIcon_shouldReturnMetadataForFilename() throws Exception {
        assertThat(Functions.tryGetIcon("help.svg"), is(not(nullValue())));
    }

    @Test
    public void tryGetIcon_shouldReturnMetadataForUrl() throws Exception {
        assertThat(Functions.tryGetIcon("48x48/green.gif"), is(not(nullValue())));
    }

    @Test
    public void tryGetIcon_shouldReturnNullForUnknown() throws Exception {
        assertThat(Functions.tryGetIcon("icon-nosuchicon"), is(nullValue()));
    }

    @Test
    public void guessIcon() throws Exception {
        Jenkins.RESOURCE_PATH = "/static/12345678";
        assertEquals("/jenkins/static/12345678/images/48x48/green.gif", Functions.guessIcon("jenkins/images/48x48/green.gif", "/jenkins"));
        assertEquals("/jenkins/static/12345678/images/48x48/green.gif", Functions.guessIcon("/jenkins/images/48x48/green.gif", "/jenkins"));
        assertEquals("/static/12345678/images/48x48/green.gif", Functions.guessIcon("images/48x48/green.gif", ""));
        assertEquals("/jenkins/static/12345678/images/48x48/green.gif", Functions.guessIcon("images/48x48/green.gif", "/jenkins"));
        assertEquals("/jenkins/static/12345678/images/48x48/green.gif", Functions.guessIcon("/images/48x48/green.gif", "/jenkins"));
        assertEquals("/images/static/12345678/images/48x48/green.gif", Functions.guessIcon("/images/48x48/green.gif", "/images"));
        assertEquals("/static/12345678/plugin/myartifactId/images/48x48/green.gif", Functions.guessIcon("/plugin/myartifactId/images/48x48/green.gif", ""));
        assertEquals("/jenkins/static/12345678/plugin/myartifactId/images/48x48/green.gif", Functions.guessIcon("/plugin/myartifactId/images/48x48/green.gif", "/jenkins"));
        assertEquals("/jenkins/static/12345678/plugin/myartifactId/images/48x48/green.gif", Functions.guessIcon("/jenkins/plugin/myartifactId/images/48x48/green.gif", "/jenkins"));
        assertEquals("/plugin/static/12345678/plugin/myartifactId/images/48x48/green.gif", Functions.guessIcon("/plugin/myartifactId/images/48x48/green.gif", "/plugin"));
        assertEquals("/plugin/static/12345678/plugin/myartifactId/images/48x48/green.gif", Functions.guessIcon("/plugin/plugin/myartifactId/images/48x48/green.gif", "/plugin"));
        assertEquals("http://acme.com/icon.svg", Functions.guessIcon("http://acme.com/icon.svg", "/jenkins"));
        assertEquals("https://acme.com/icon.svg", Functions.guessIcon("https://acme.com/icon.svg", "/jenkins"));
    }
}
