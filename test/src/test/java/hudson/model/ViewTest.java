/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.model;

import jenkins.model.Jenkins;
import org.jvnet.hudson.test.Bug;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import hudson.XmlFile;
import org.jvnet.hudson.test.Email;
import org.w3c.dom.Text;

import static hudson.model.Messages.Hudson_ViewName;
import java.io.File;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Bug(7100)
    @Test public void xHudsonHeader() throws Exception {
        assertNotNull(j.createWebClient().goTo("").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test public void conflictingName() throws Exception {
        assertNull(j.jenkins.getView("foo"));

        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        assertNotNull(j.jenkins.getView("foo"));

        // do it again and verify an error
        try {
            j.submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test public void privateView() throws Exception {
        j.createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = j.createWebClient();
        HtmlPage userPage = wc.goTo("user/me");
        HtmlAnchor privateViewsLink = userPage.getFirstAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = (HtmlPage) privateViewsLink.click();

        Text viewLabel = (Text) privateViewsPage.getFirstByXPath("//table[@id='viewList']//td[@class='active']/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = new ListView("listView", j.jenkins);
        j.jenkins.addView(listView);

        HtmlPage newViewPage = wc.goTo("user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("proxy-view");
        ((HtmlRadioButtonInput) form.getInputByValue("hudson.model.ProxyView")).setChecked(true);
        HtmlPage proxyViewConfigurePage = j.submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        j.submit(form);

        assertTrue(proxyView instanceof ProxyView);
        assertEquals(((ProxyView) proxyView).getProxiedViewName(), "listView");
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }

    @Test public void deleteView() throws Exception {
        WebClient wc = j.createWebClient();

        ListView v = new ListView("list", j.jenkins);
        j.jenkins.addView(v);
        HtmlPage delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(j.jenkins.getView("list"));

        User user = User.get("user", true);
        MyViewsProperty p = user.getProperty(MyViewsProperty.class);
        v = new ListView("list", p);
        p.addView(v);
        delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(p.getView("list"));

    }

    @Bug(9367)
    @Test public void persistence() throws Exception {
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);

        ListView v = (ListView) Jenkins.XSTREAM.fromXML(Jenkins.XSTREAM.toXML(view));
        System.out.println(v.getProperties());
        assertNotNull(v.getProperties());
    }

    @Bug(9367)
    @Test public void allImagesCanBeLoaded() throws Exception {
        User.get("user", true);
        WebClient webClient = j.createWebClient();
        webClient.setJavaScriptEnabled(false);
        j.assertAllImageLoadSuccessfully(webClient.goTo("asynchPeople"));
    }

    @Bug(16608)
    @Test public void notAllowedName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("..");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);

        try {
            j.submit(form);
            fail("\"..\" should not be allowed.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Ignore("verified manually in Winstone but org.mortbay.JettyResponse.sendRedirect (6.1.26) seems to mangle the location")
    @Bug(18373)
    @Test public void unicodeName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        String name = "I ♥ NY";
        form.getInputByName("name").setValueAttribute(name);
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        View view = j.jenkins.getView(name);
        assertNotNull(view);
        j.submit(j.createWebClient().getPage(view, "configure").getFormByName("viewConfig"));
    }

    @Bug(17302)
    @Test public void doConfigDotXml() throws Exception {
        ListView view = new ListView("v", j.jenkins);
        view.description = "one";
        j.jenkins.addView(view);
        WebClient wc = j.createWebClient();
        String xml = wc.goToXml("view/v/config.xml").getContent();
        assertTrue(xml, xml.contains("<description>one</description>"));
        xml = xml.replace("<description>one</description>", "<description>two</description>");
        WebRequestSettings req = new WebRequestSettings(wc.createCrumbedUrl("view/v/config.xml"), HttpMethod.POST);
        req.setRequestBody(xml);
        wc.getPage(req);
        assertEquals("two", view.getDescription());
        xml = new XmlFile(Jenkins.XSTREAM, new File(j.jenkins.getRootDir(), "config.xml")).asString();
        assertTrue(xml, xml.contains("<description>two</description>"));
    }

}
