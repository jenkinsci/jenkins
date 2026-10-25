package jenkins.model.details;

import hudson.Extension;
import hudson.ExtensionList;

@Extension
public class GeneralDetailGroup extends DetailGroup {

    public static GeneralDetailGroup get() {
        return ExtensionList.lookupSingleton(GeneralDetailGroup.class);
    }
}
