/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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


import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.Collection;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.core.StringContains.containsString;


public class ItemGroupMixInTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Bug(20951)
    @LocalData
    @Test public void xmlFileReadCannotResolveClassException() throws Exception {
        MockFolder d = r.jenkins.getItemByFullName("d", MockFolder.class);
        assertNotNull(d);
        Collection<TopLevelItem> items = d.getItems();
        assertEquals(1, items.size());
        assertEquals("valid", items.iterator().next().getName());
    }

    @Test public void createProjectFromXMLShouldNoCreateEntities() throws IOException {

        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[\n" +
                "  <!ENTITY foo SYSTEM \"file:///\">\n" +
                "]>\n" +
                "<project>\n" +
                "  <actions/>\n" +
                "  <description>&foo;</description>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>true</canRoam>\n" +
                "  <triggers/>\n" +
                "  <builders/>\n" +
                "  <publishers/>\n" +
                "  <buildWrappers/>\n" +
                "</project>";

        Item foo = r.jenkins.createProjectFromXML("foo", new ByteArrayInputStream(xml.getBytes()));
        // if no exception then JAXP is swallowing these - so there should be no entity in the description.
        assertThat(Items.getConfigFile(foo).asString(), containsString("<description/>"));
    }
}
