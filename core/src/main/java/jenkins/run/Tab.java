package jenkins.run;

import hudson.model.Action;
import hudson.model.Actionable;

public abstract class Tab implements Action, Badgeable {

    protected Actionable object;

    public Tab(Actionable object) {
        this.object = object;
    }

    public Actionable getObject() {
        return object;
    }
}
