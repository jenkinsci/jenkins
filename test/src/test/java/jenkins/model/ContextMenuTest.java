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

package jenkins.model;

import static jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import static jenkins.model.ModelObjectWithContextMenu.ContextMenuVisibility;
import static jenkins.model.ModelObjectWithContextMenu.MenuItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.TransientProjectActionFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;

@For(ContextMenu.class)
@WithJenkins
class ContextMenuTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-19173")
    @Test
    void contextMenuVisibility() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject("p");
        Callable<ContextMenu> doContextMenu = () -> p.doContextMenu(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
        ActionFactory f = j.jenkins.getExtensionList(TransientProjectActionFactory.class).get(ActionFactory.class);
        f.visible = true;
        ContextMenu menu = j.executeOnServer(doContextMenu);
        Map<String, String> parsed = parse(menu);
        assertEquals("Hello", parsed.get("testing"), parsed.toString());
        f.visible = false;
        menu = j.executeOnServer(doContextMenu);
        parsed = parse(menu);
        assertNull(parsed.get("testing"), parsed.toString());
    }

    @TestExtension public static class ActionFactory extends TransientProjectActionFactory {

        boolean visible;

        @SuppressWarnings("rawtypes")
        @Override public Collection<? extends Action> createFor(AbstractProject target) {
            return Collections.singleton(new ContextMenuVisibility() {
                @Override public boolean isVisible() {
                    return visible;
                }

                @Override public String getIconFileName() {
                    return "whatever";
                }

                @Override public String getDisplayName() {
                    return "Hello";
                }

                @Override public String getUrlName() {
                    return "testing";
                }
            });
        }

    }

    private static Map<String, String> parse(ContextMenu menu) {
        Map<String, String> r = new TreeMap<>();
        for (MenuItem mi : menu.items) {
            r.put(mi.url.replaceFirst("^.*/(.)", "$1"), mi.displayName);
        }
        return r;
    }

}
