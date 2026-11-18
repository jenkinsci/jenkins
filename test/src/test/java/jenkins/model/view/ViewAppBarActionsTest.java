/*
 * The MIT License
 *
 * Copyright (c) 2026, Jan Faracik
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

package jenkins.model.view;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.model.Action;
import hudson.model.ListView;
import hudson.model.User;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlagsProperty;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import jenkins.model.menu.event.SplitButtonEvent;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ViewAppBarActionsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }

    @Test
    void viewMenuIsUnavailableWithoutTheFlag() throws Exception {
        User.getById("alice", true);

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            Page page = wc.goTo("view/all/contextMenu?menu-only=true", null);

            assertThat(page.getWebResponse().getStatusCode(), is(404));
        }
    }

    @Test
    void viewMenuResolvesActionsAgainstTheView() throws Exception {
        enableFlag();
        new File(j.jenkins.getRootDir(), "fingerprints").mkdirs();

        Map<String, JSONObject> items = menu("view/all/contextMenu?menu-only=true");

        assertThat(items.keySet(), hasItems("Build History", "Atom feed", "Legend", "Project Relationship"));
        assertThat(items.keySet(), not(hasItem("New Item")));
        assertThat(items.get("Build History"), hasEntry("baseUrl", j.contextPath + "/view/all/"));
        assertThat(items.get("Project Relationship").getJSONObject("event"),
                hasEntry("url", j.contextPath + "/projectRelationship"));
    }

    @Test
    void folderViewMenuResolvesFolderActionsAgainstTheFolder() throws Exception {
        enableFlag();
        MockFolder folder = j.createFolder("folder");
        folder.addView(new ListView("other", folder));

        Map<String, JSONObject> items = menu("job/folder/view/other/contextMenu?menu-only=true");

        assertThat(items.get("Build History"), hasEntry("baseUrl", j.contextPath + "/job/folder/view/other/"));
        assertThat(items.get("Folder action"), hasEntry("baseUrl", j.contextPath + "/job/folder/"));
        assertThat(items.keySet(), hasItem("Delete View"));
        assertThat(items.get("Root action").getJSONObject("event"), hasEntry("url", j.contextPath + "/root-action"));

        JSONObject nested = items.get("Dropdown action").getJSONObject("subMenu").getJSONArray("items").getJSONObject(0);
        assertThat(nested.getJSONObject("event"), hasEntry("url", j.contextPath + "/nested-action"));
        assertThat(nested, hasEntry("baseUrl", j.contextPath + "/job/folder/"));

        JSONObject splitButton = items.get("Split action").getJSONObject("event");
        assertThat(splitButton.getJSONObject("event"), hasEntry("url", j.contextPath + "/split-action"));
    }

    @Test
    void folderContextMenuHidesViewOnlyActions() throws Exception {
        enableFlag();
        j.createFolder("folder");

        Map<String, JSONObject> items = menu("job/folder/contextMenu");

        assertThat(items.keySet(), hasItem("Folder action"));
        assertThat(items.keySet(), not(hasItem("Build History")));
        assertThat(items.keySet(), not(hasItem("Atom feed")));
        assertThat(items.keySet(), not(hasItem("Legend")));
        assertThat(items.keySet(), not(hasItem("Project Relationship")));
        assertThat(items.keySet(), not(hasItem("Check File Fingerprint")));
    }

    private void enableFlag() throws Exception {
        User.getById("alice", true)
                .addProperty(new UserExperimentalFlagsProperty(Map.of("new-dashboard-page.flag", "true")));
    }

    private Map<String, JSONObject> menu(String path) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            String json = wc.goTo(path, "application/json").getWebResponse().getContentAsString();
            JSONArray items = JSONObject.fromObject(json).getJSONArray("items");

            Map<String, JSONObject> result = new LinkedHashMap<>();
            for (Object item : items) {
                JSONObject menuItem = (JSONObject) item;
                result.put(menuItem.optString("displayName"), menuItem);
            }

            return result;
        }
    }

    private record TestAction(String displayName, String urlName) implements Action {

        @Override
        public String getIconFileName() {
            return "symbol-settings";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getUrlName() {
            return urlName;
        }
    }

    private record EventAction(String displayName, Event event) implements Action {

        @Override
        public String getIconFileName() {
            return "symbol-settings";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getUrlName() {
            return null;
        }

        @Override
        public Event getEvent() {
            return event;
        }
    }

    @TestExtension
    public static final class FolderActionFactory extends TransientActionFactory<MockFolder> {

        @Override
        public Class<MockFolder> type() {
            return MockFolder.class;
        }

        @Override
        public Collection<? extends Action> createFor(MockFolder target) {
            Action nested = new TestAction("Nested action", "/nested-action");

            return Set.of(
                    new TestAction("Folder action", "folder-action"),
                    new TestAction("Root action", "/root-action"),
                    new EventAction("Dropdown action", DropdownEvent.of(List.of(nested))),
                    new EventAction("Split action", SplitButtonEvent.of(LinkEvent.of("/split-action"), List.of(nested))));
        }
    }
}
