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

import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class FunctionsTest {
    @Test
    public void testGetActionUrl_absoluteUriWithAuthority(){
        String[] uris = {
            "http://example.com/foo/bar",
            "https://example.com/foo/bar",
            "ftp://example.com/foo/bar",
            "svn+ssh://nobody@example.com/foo/bar",
        };
        for(String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    @Test
    @Issue("JENKINS-7725")
    public void testGetActionUrl_absoluteUriWithoutAuthority(){
        String[] uris = {
            "mailto:nobody@example.com",
            "mailto:nobody@example.com?subject=hello",
            "javascript:alert('hello')",
        };
        for(String uri : uris) {
            String result = Functions.getActionUrl(null, createMockAction(uri));
            assertEquals(uri, result);
        }
    }

    @Test
    @PrepareForTest(Stapler.class)
    public void testGetActionUrl_absolutePath() throws Exception{
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        String[] paths = {
            "/",
            "/foo/bar",
        };
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        for(String path : paths) {
            String result = Functions.getActionUrl(null, createMockAction(path));
            assertEquals(contextPath + path, result);
        }
    }

    @Test
    @PrepareForTest(Stapler.class)
    public void testGetActionUrl_relativePath() throws Exception{
        String contextPath = "/jenkins";
        String itUrl = "iturl/";
        StaplerRequest req = createMockRequest(contextPath);
        String[] paths = {
            "foo/bar",
            "./foo/bar",
            "../foo/bar",
        };
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        for(String path : paths) {
            String result = Functions.getActionUrl(itUrl, createMockAction(path));
            assertEquals(contextPath + "/" + itUrl + path, result);
        }
    }
    
    @Test
    @PrepareForTest({Stapler.class, Jenkins.class})
    public void testGetRelativeLinkTo_JobContainedInView() throws Exception{
        Jenkins j = createMockJenkins();
        ItemGroup parent = j;
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        View view = mock(View.class);
        when(view.getOwnerItemGroup()).thenReturn(parent);
        createMockAncestors(req, createAncestor(view, "."), createAncestor(j, "../.."));
        TopLevelItem i = createMockItem(parent, "job/i/");
        when(view.getItems()).thenReturn(Arrays.asList(i));
        String result = Functions.getRelativeLinkTo(i);
        assertEquals("job/i/", result);
    }

    @Test
    @PrepareForTest({Stapler.class, Jenkins.class})
    public void testGetRelativeLinkTo_JobFromComputer() throws Exception{
        Jenkins j = createMockJenkins();
        ItemGroup parent = j;
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        Computer computer = mock(Computer.class);
        createMockAncestors(req, createAncestor(computer, "."), createAncestor(j, "../.."));
        TopLevelItem i = createMockItem(parent, "job/i/");
        String result = Functions.getRelativeLinkTo(i);
        assertEquals("/jenkins/job/i/", result);
    }

    @Ignore("too expensive to make it correct")
    @Test
    @PrepareForTest({Stapler.class, Jenkins.class})
    public void testGetRelativeLinkTo_JobNotContainedInView() throws Exception{
        Jenkins j = createMockJenkins();
        ItemGroup parent = j;
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        View view = mock(View.class);
        when(view.getOwnerItemGroup()).thenReturn(parent);
        createMockAncestors(req, createAncestor(j, "../.."), createAncestor(view, "."));
        TopLevelItem i = createMockItem(parent, "job/i/");
        when(view.getItems()).thenReturn(Collections.<TopLevelItem>emptyList());
        String result = Functions.getRelativeLinkTo(i);
        assertEquals("/jenkins/job/i/", result);
    }
    
    private interface TopLevelItemAndItemGroup <T extends TopLevelItem> extends TopLevelItem, ItemGroup<T> {}
    
    @Test
    @PrepareForTest({Stapler.class,Jenkins.class})
    public void testGetRelativeLinkTo_JobContainedInViewWithinItemGroup() throws Exception{
        Jenkins j = createMockJenkins();
        TopLevelItemAndItemGroup parent = mock(TopLevelItemAndItemGroup.class);
        when(parent.getShortUrl()).thenReturn("parent/");
        String contextPath = "/jenkins";
        StaplerRequest req = createMockRequest(contextPath);
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        View view = mock(View.class);
        when(view.getOwnerItemGroup()).thenReturn(parent);
        createMockAncestors(req, createAncestor(j, "../../.."), createAncestor(parent, "../.."), createAncestor(view, "."));
        TopLevelItem i = createMockItem(parent, "job/i/", "parent/job/i/");
        when(view.getItems()).thenReturn(Arrays.asList(i));
        String result = Functions.getRelativeLinkTo(i);
        assertEquals("job/i/", result);
    }

    @Issue("JENKINS-17713")
    @PrepareForTest({Stapler.class, Jenkins.class})
    @Test public void getRelativeLinkTo_MavenModules() throws Exception {
        Jenkins j = createMockJenkins();
        StaplerRequest req = createMockRequest("/jenkins");
        mockStatic(Stapler.class);
        when(Stapler.getCurrentRequest()).thenReturn(req);
        TopLevelItemAndItemGroup ms = mock(TopLevelItemAndItemGroup.class);
        when(ms.getShortUrl()).thenReturn("job/ms/");
        // TODO "." (in second ancestor) is what Stapler currently fails to do. Could edit test to use ".." but set a different request path?
        createMockAncestors(req, createAncestor(j, "../.."), createAncestor(ms, "."));
        Item m = mock(Item.class);
        when(m.getParent()).thenReturn(ms);
        when(m.getShortUrl()).thenReturn("grp$art/");
        assertEquals("grp$art/", Functions.getRelativeLinkTo(m));
    }

    @Test
    public void testGetRelativeDisplayName() {
        Item i = mock(Item.class);
        when(i.getName()).thenReturn("jobName");
        when(i.getFullDisplayName()).thenReturn("displayName");
        assertEquals("displayName",Functions.getRelativeDisplayNameFrom(i, null));
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

    private void createMockAncestors(StaplerRequest req, Ancestor... ancestors) {
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

    private Jenkins createMockJenkins() {
        mockStatic(Jenkins.class);
        Jenkins j = mock(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(j);
        return j;
    }
    
    private static Ancestor createAncestor(Object o, String relativePath) {
        Ancestor a = mock(Ancestor.class);
        when(a.getObject()).thenReturn(o);
        when(a.getRelativePath()).thenReturn(relativePath);
        return a;
    }

    @Test
    @PrepareForTest(Stapler.class)
    public void testGetActionUrl_unparsable() throws Exception{
        assertEquals(null, Functions.getActionUrl(null, createMockAction("http://nowhere.net/stuff?something=^woohoo")));
    }

    private static Action createMockAction(String uri) {
        Action action = mock(Action.class);
        when(action.getUrlName()).thenReturn(uri);
        return action;
    }

    private static StaplerRequest createMockRequest(String contextPath) {
        StaplerRequest req = mock(StaplerRequest.class);
        when(req.getContextPath()).thenReturn(contextPath);
        return req;
    }

    @Test
    @Issue("JENKINS-16630")
    public void testHumanReadableFileSize(){
        Locale defaultLocale = Locale.getDefault();
        try{
            Locale.setDefault(Locale.ENGLISH);
            assertEquals("0 B", Functions.humanReadableByteSize(0));
            assertEquals("1023 B", Functions.humanReadableByteSize(1023));
            assertEquals("1.00 KB", Functions.humanReadableByteSize(1024));
            assertEquals("1.50 KB", Functions.humanReadableByteSize(1536));
            assertEquals("20.00 KB", Functions.humanReadableByteSize(20480));
            assertEquals("1023.00 KB", Functions.humanReadableByteSize(1047552));
            assertEquals("1.00 MB", Functions.humanReadableByteSize(1048576));
            assertEquals("1.50 GB", Functions.humanReadableByteSize(1610612700));
        }finally{
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
                Util.join(Arrays.asList(chunks), "<wbr>"),
                Functions.breakableString(plain)
        );
    }

    @Issue("JENKINS-20800")
    @Test public void printLogRecordHtml() throws Exception {
        LogRecord lr = new LogRecord(Level.INFO, "Bad input <xml/>");
        lr.setLoggerName("test");
        assertEquals("Bad input &lt;xml/&gt;\n", Functions.printLogRecordHtml(lr, null)[3]);
    }

}
