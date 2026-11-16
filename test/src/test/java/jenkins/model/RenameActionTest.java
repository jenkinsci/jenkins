package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.FreeStyleProject;
import jenkins.model.menu.Group;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RenameActionTest {

    @Test
    void renameActionUsesFirstInMenuGroup() {
        assertEquals(Group.FIRST_IN_MENU, new RenameAction().getGroup());
    }

    @Test
    void renameActionOnProjectUsesFirstInMenuGroup(JenkinsRule r) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject();
        RenameAction renameAction = project.getAction(RenameAction.class);
        assertNotNull(renameAction);
        assertEquals(Group.FIRST_IN_MENU, renameAction.getGroup());
    }
}
