package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;

/**
 * Convenient base class for extensions that contributes to the global security configuration page but nothing else.
 *
 * <h2>Views</h2>
 * <p>
 * Subtypes of this class should define <tt>config.groovy</tt> that gets pulled into the global security configuration page.
 * 
 *
 * @author Dominik Bartholdi
 * @since 1.494
 */
public abstract class GlobalSecurityConfigurationPart extends Descriptor<GlobalSecurityConfigurationPart> implements ExtensionPoint, Describable<GlobalSecurityConfigurationPart>  {
    protected GlobalSecurityConfigurationPart() {
        super(self());
    }

    public final Descriptor<GlobalSecurityConfigurationPart> getDescriptor() {
        return this;
    }

    /**
     * Unless this object has additional web presence, display name is not used at all.
     * So default to "".
     */
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getGlobalConfigPage() {
        return getConfigPage();
    }

    /**
     * Returns all the registered {@link GlobalSecurityConfigurationPart} descriptors.
     */
    public static ExtensionList<GlobalSecurityConfigurationPart> all() {
        return Jenkins.getInstance().<GlobalSecurityConfigurationPart,GlobalSecurityConfigurationPart>getDescriptorList(GlobalSecurityConfigurationPart.class);
        // pointless type parameters help work around bugs in javac in earlier versions http://codepad.org/m1bbFRrH
    }
}
