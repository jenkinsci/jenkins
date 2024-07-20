package jenkins.model.experimentalflags;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;

@Extension
public class RemoveYuiUserExperimentalFlag extends BooleanUserExperimentalFlag {
    public RemoveYuiUserExperimentalFlag() {
        super("remove-yui.flag");
    }

    @Override
    public String getDisplayName() {
        return "Remove YUI";
    }

    @Nullable
    @Override
    public String getShortDescription() {
        return "Remove YUI from all Jenkins UI pages. This will break anything that depends on YUI";
    }
}
