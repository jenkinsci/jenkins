package jenkins.model;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
/**
 * Participates in the rendering of the login page
 *
 * <p>
 * This class provides a few hooks to augment the HTML of the login page.
 *
 * @since TODO
 */
public class LoginPageDecorator extends Descriptor<LoginPageDecorator> implements ExtensionPoint, Describable<LoginPageDecorator> {

    protected LoginPageDecorator()  {
        super(self());
    }

    @Override
    public final Descriptor<LoginPageDecorator> getDescriptor() {
        return this;
    }
    /**
     * Obtains the URL of this object, excluding the context path.
     *
     * <p>
     * Every {@link LoginPageDecorator} is bound to URL via {@link Jenkins#getDescriptor()}.
     * This method returns such an URL.
     */
    public final String getUrl() {
        return "descriptor/"+clazz.getName();
    }

    /**
     * The first found LoginDecarator, there can only be one.
     * @return the first found {@link LoginPageDecorator}
     */
    public static LoginPageDecorator first(){
        DescriptorExtensionList<LoginPageDecorator, LoginPageDecorator> descriptorList = Jenkins.getInstanceOrNull().<LoginPageDecorator, LoginPageDecorator>getDescriptorList(LoginPageDecorator.class);
        return descriptorList.get(0);
    }

}
