/*
 * The MIT License
 *
 * Copyright 2026 Jenkins Contributors.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import hudson.model.Action;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import jenkins.model.ModelObjectWithContextMenu.MenuItem;
import jenkins.model.menu.event.ConfirmationEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;

@For(ContextMenu.class)
@WithJenkins
class ContextMenuRootRelativeTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void rootRelativeUrlsInJobContextMenu() throws Exception {
        j.executeOnServer(() -> {
            ContextMenu menu = new ContextMenu();

            // Default GET LinkEvent for root-relative URL (/manage)
            Action manageAction = new Action() {
                @Override
                public String getIconFileName() {
                    return "symbol-settings";
                }

                @Override
                public String getDisplayName() {
                    return "Manage";
                }

                @Override
                public String getUrlName() {
                    return "/manage";
                }
            };

            // Root-relative URL with a POST LinkEvent
            Action postAction = new Action() {
                @Override
                public String getIconFileName() {
                    return "symbol-play";
                }

                @Override
                public String getDisplayName() {
                    return "Post Action";
                }

                @Override
                public String getUrlName() {
                    return "/postAction";
                }

                @Override
                public Event getEvent() {
                    return LinkEvent.of("/postAction", LinkEvent.LinkEventType.POST);
                }
            };

            // Custom event (e.g. ConfirmationEvent) which should not be rewritten
            ConfirmationEvent confirmationEvent = ConfirmationEvent.of("Are you sure?", null, "/custom");
            Action confirmationAction = new Action() {
                @Override
                public String getIconFileName() {
                    return "symbol-trash";
                }

                @Override
                public String getDisplayName() {
                    return "Confirm Action";
                }

                @Override
                public String getUrlName() {
                    return "/confirmAction";
                }

                @Override
                public Event getEvent() {
                    return confirmationEvent;
                }
            };

            menu.add(manageAction);
            menu.add(postAction);
            menu.add(confirmationAction);

            assertEquals(3, menu.items.size());

            String contextPath = Stapler.getCurrentRequest2().getContextPath();
            assertEquals("/jenkins", contextPath);

            // 1. /manage -> /jenkins/manage (GET type kept)
            MenuItem item0 = menu.items.get(0);
            assertEquals(contextPath + "/manage", item0.url);
            assertInstanceOf(LinkEvent.class, item0.getEvent());
            LinkEvent le0 = (LinkEvent) item0.getEvent();
            assertEquals(contextPath + "/manage", le0.getUrl());
            assertEquals(LinkEvent.LinkEventType.GET, le0.getType());

            // 2. /postAction -> /jenkins/postAction (POST type kept)
            MenuItem item1 = menu.items.get(1);
            assertEquals(contextPath + "/postAction", item1.url);
            assertInstanceOf(LinkEvent.class, item1.getEvent());
            LinkEvent le1 = (LinkEvent) item1.getEvent();
            assertEquals(contextPath + "/postAction", le1.getUrl());
            assertEquals(LinkEvent.LinkEventType.POST, le1.getType());

            // 3. ConfirmationEvent kept untouched
            MenuItem item2 = menu.items.get(2);
            assertEquals(confirmationEvent, item2.getEvent());

            return null;
        });
    }
}
