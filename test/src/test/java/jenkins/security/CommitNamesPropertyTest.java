/*
 * The MIT License
 *
 * Copyright (c) 2012, Daniel Khodaparast
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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.User;

import junit.framework.Assert;

import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Daniel Khodaparast
 * @since 1.477
 */
public class CommitNamesPropertyTest extends HudsonTestCase {

	@Test
	public void testNotDefined() throws Exception {
		jenkins.setUseCommitNames(true);
		User u = User.get("test");

		HtmlPage config = createWebClient().goTo(u.getUrl() + "/configure");
		HtmlForm form = config.getFormByName("config");

		assertEquals("", form.getInputByName("commit.names").getValueAttribute());
	}

	@Test
	public void testNotDuplicate() throws Exception {
		jenkins.setUseCommitNames(true);
		User u = User.get("test");

		WebClient wc = createWebClient();
		HtmlPage config = wc.goTo(u.getUrl() + "/configure");
		HtmlForm form = config.getFormByName("config");

		form.getInputByName("commit.names").setValueAttribute("foo,bar,foo");
		submit(form);

		CommitNamesProperty c = u.getProperty(CommitNamesProperty.class);
		assertEquals("foo,bar", c.getNames());
	}

	@Test
	public void testNotEnabled() throws Exception {
		jenkins.setUseCommitNames(false);
		User u = User.get("test");

		HtmlPage config = createWebClient().goTo(u.getUrl() + "/configure");
		HtmlForm form = config.getFormByName("config");

		assertEquals("", form.getInputByName("commit.names").getValueAttribute());
	}


	@Test
	public void testUserExists() throws Exception {
		jenkins.setUseCommitNames(true);
		User u1 = User.get("test1");
		User u2 = User.get("test2");

		WebClient wc = createWebClient();
		HtmlPage config = wc.goTo(u1.getUrl() + "/configure");
		HtmlForm form = config.getFormByName("config");

		form.getInputByName("commit.names").setValueAttribute("foo,test2");
		submit(form);

		CommitNamesProperty c = u1.getProperty(CommitNamesProperty.class);
		assertEquals("foo", c.getNames());
	}

	@Test
	public void testUserTrimmed() throws Exception {
		jenkins.setUseCommitNames(true);
		User u = User.get("test");

		WebClient wc = createWebClient();
		HtmlPage config = wc.goTo(u.getUrl() + "/configure");
		HtmlForm form = config.getFormByName("config");

		form.getInputByName("commit.names").setValueAttribute("   foo      ,       bar,foo.bar   ,   bar.foo,    ");
		submit(form);

		CommitNamesProperty c = u.getProperty(CommitNamesProperty.class);
		assertEquals("foo,bar,foo.bar,bar.foo", c.getNames());
	}
}