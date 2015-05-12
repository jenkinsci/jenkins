package hudson.model;

import hudson.Extension;
import jenkins.model.Jenkins;

public class LabelTestProject extends Project<LabelTestProject,LabelTestBuild> implements TopLevelItem {
    public LabelTestProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    //Override AbstractProject's label assignment to preserve the label instance
    Label l = null;

    @Override
    public void setAssignedLabel(Label l) {
        this.l = l;
    }

    @Override
    public Label getAssignedLabel() {
        return l;
    }

    @Override
    protected Class<LabelTestBuild> getBuildClass() {
        return LabelTestBuild.class;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension(ordinal=1000)
    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return "LabelTestProject";
        }

        public LabelTestProject newInstance(ItemGroup parent, String name) {
            return new LabelTestProject(parent,name);
        }
    }
}

