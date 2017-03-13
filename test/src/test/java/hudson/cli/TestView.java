package hudson.cli;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.ListView;

/**
 * Created by shebert on 14/03/17.
 */
public class TestView extends ListView {

    protected TestView(String name) {
        super(name);
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void addAliases() {
        Items.XSTREAM2.addCompatibilityAlias("org.acme.old.Foo", TestView.class);
    }
}
