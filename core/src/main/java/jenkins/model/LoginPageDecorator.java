package jenkins.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;

public class LoginPageDecorator extends Descriptor<LoginPageDecorator> implements ExtensionPoint, Describable<LoginPageDecorator> {

    protected LoginPageDecorator()  {
        super(self());
    }

    @Override
    public final Descriptor<LoginPageDecorator> getDescriptor() {
        return this;
    }

    public final String getUrl() {
        return "descriptor/"+clazz.getName();
    }

    public static LoginPageDecorator first(){
        if(Jenkins.getInstanceOrNull()==null) {
            return null;
        } else {
            DescriptorExtensionList<LoginPageDecorator, LoginPageDecorator> descriptorList = Jenkins.getInstanceOrNull().<LoginPageDecorator, LoginPageDecorator>getDescriptorList(LoginPageDecorator.class);
            return descriptorList.get(0);
        }
    }

}
