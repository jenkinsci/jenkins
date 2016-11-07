package hudson.model;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import java.util.ArrayList;
import java.util.List;

public class ActionableTest {

    static class ActionableOverride extends Actionable {
        ArrayList<Action> specialActions = new ArrayList<Action>();

        @Override
        public String getDisplayName() {
            return "nope";
        }

        @Override
        public String getSearchUrl() {
            return "morenope";
        }

        @Override
        public List<Action> getActions() {
            return specialActions;
        }
    }

    @Issue("JENKINS-39555")
    @Test
    public void testExtensionOverrides() throws Exception {
        ActionableOverride myOverridden = new ActionableOverride();
        InvisibleAction invis = new InvisibleAction() {
        };
        myOverridden.addAction(invis);
        Assert.assertArrayEquals(new Object[]{invis}, myOverridden.specialActions.toArray());
        Assert.assertArrayEquals(new Object[]{invis}, myOverridden.getActions().toArray());

        myOverridden.getActions().remove(invis);
        Assert.assertArrayEquals(new Object[]{}, myOverridden.specialActions.toArray());
        Assert.assertArrayEquals(new Object[]{}, myOverridden.getActions().toArray());

        InvisibleAction invis2 = new InvisibleAction() {};
        myOverridden.addOrReplaceAction(invis2);
        Assert.assertArrayEquals(new Object[]{invis2}, myOverridden.specialActions.toArray());
        Assert.assertArrayEquals(new Object[]{invis2}, myOverridden.getActions().toArray());

        myOverridden.addOrReplaceAction(invis);
        myOverridden.addOrReplaceAction(invis);
        Assert.assertArrayEquals(new Object[]{invis2, invis}, myOverridden.specialActions.toArray());
        Assert.assertArrayEquals(new Object[]{invis2, invis}, myOverridden.getActions().toArray());
    }
}
