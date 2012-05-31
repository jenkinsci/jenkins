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
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.w3c.dom.Text;

import static hudson.model.Messages.Hudson_ViewName;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest extends HudsonTestCase {

    @Bug(7100)
    public void testXHudsonHeader() throws Exception {
        assertNotNull(new WebClient().goTo("/").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

	/**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    public void testConflictingName() throws Exception {
        assertNull(jenkins.getView("foo"));

        HtmlForm form = new WebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        submit(form);
        assertNotNull(jenkins.getView("foo"));

        // do it again and verify an error
        try {
            submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400,e.getStatusCode());
        }
    }

    public void testPrivateView() throws Exception {
        createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = new WebClient();
        HtmlPage userPage = wc.goTo("/user/me");
        HtmlAnchor privateViewsLink = userPage.getFirstAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = (HtmlPage) privateViewsLink.click();

        Text viewLabel = (Text) privateViewsPage.getFirstByXPath("//table[@id='viewList']//td[@class='active']/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = new ListView("listView", jenkins);
        jenkins.addView(listView);

        HtmlPage newViewPage = wc.goTo("/user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("proxy-view");
        ((HtmlRadioButtonInput) form.getInputByValue("hudson.model.ProxyView")).setChecked(true);
        HtmlPage proxyViewConfigurePage = submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        submit(form);

        assertTrue(proxyView instanceof ProxyView);
        assertEquals(((ProxyView) proxyView).getProxiedViewName(), "listView");
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }
    
    public void testDeleteView() throws Exception {
    	WebClient wc = new WebClient();

    	ListView v = new ListView("list", jenkins);
		jenkins.addView(v);
    	HtmlPage delete = wc.getPage(v, "delete");
    	submit(delete.getFormByName("delete"));
    	assertNull(jenkins.getView("list"));
    	
    	User user = User.get("user", true);
    	MyViewsProperty p = user.getProperty(MyViewsProperty.class);
    	v = new ListView("list", p);
		p.addView(v);
    	delete = wc.getPage(v, "delete");
    	submit(delete.getFormByName("delete"));
    	assertNull(p.getView("list"));
    	
    }

    @Bug(9367)
    public void testPersistence() throws Exception {
        ListView view = new ListView("foo", jenkins);
        jenkins.addView(view);

        ListView v = (ListView) Jenkins.XSTREAM.fromXML(Jenkins.XSTREAM.toXML(view));
        System.out.println(v.getProperties());
        assertNotNull(v.getProperties());
    }

    @Bug(9367)
    public void testAllImagesCanBeLoaded() throws Exception {
        User.get("user", true);
        WebClient webClient = new WebClient();
        webClient.setJavaScriptEnabled(false);
        assertAllImageLoadSuccessfully(webClient.goTo("people"));
    }
}
