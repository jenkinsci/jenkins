package hudson.cli;

import hudson.model.Item;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

class CreateJobHelper {
    public CreateJobHelper(String name) {
        Jenkins jenkins = Jenkins.getActiveInstance();

        if (jenkins.getItemByFullName(name)!=null) {
            throw new IllegalStateException("Job '"+name+"' already exists");
        }

        ModifiableTopLevelItemGroup ig = jenkins;
        int i = name.lastIndexOf('/');
        if (i > 0) {
            String group = name.substring(0, i);
            Item item = jenkins.getItemByFullName(group);
            if (item == null) {
                throw new IllegalArgumentException("Unknown ItemGroup " + group);
            }

            if (item instanceof ModifiableTopLevelItemGroup) {
                ig = (ModifiableTopLevelItemGroup) item;
            } else {
                throw new IllegalStateException("Can't create job from CLI in " + group);
            }
        }
        jobName = name.substring(i + 1);
        group = ig;
    }

    private String jobName;
    private ModifiableTopLevelItemGroup group;

    public String getName() {
        return jobName;
    }

    public ModifiableTopLevelItemGroup getGroup() {
        return group;
    }
}
