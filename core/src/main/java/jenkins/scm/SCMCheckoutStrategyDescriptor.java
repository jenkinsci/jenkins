package jenkins.scm;

import com.google.common.collect.Lists;
import hudson.DescriptorExtensionList;
import hudson.matrix.MatrixExecutionStrategyDescriptor;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.scm.SCM;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Descriptor} for {@link SCMCheckoutStrategy}.
 *
 */
public abstract class SCMCheckoutStrategyDescriptor extends Descriptor<SCMCheckoutStrategy> {
    protected SCMCheckoutStrategyDescriptor(Class<? extends SCMCheckoutStrategy> clazz) {
        super(clazz);
    }

    protected SCMCheckoutStrategyDescriptor() {
    }
    
    /**
     * Allows {@link SCMCheckoutStrategyDescriptor} to target specific kind of projects,
     * such as matrix projects.
     */
    public abstract boolean isApplicable(AbstractProject project);

    /**
     * Returns all the registered {@link MatrixExecutionStrategyDescriptor}s.
     */
    public static DescriptorExtensionList<SCMCheckoutStrategy,SCMCheckoutStrategyDescriptor> all() {
        return Jenkins.getInstance().<SCMCheckoutStrategy,SCMCheckoutStrategyDescriptor>getDescriptorList(SCMCheckoutStrategy.class);
    }
    
    public static List<SCMCheckoutStrategyDescriptor> _for(AbstractProject p) {
        List<SCMCheckoutStrategyDescriptor> r = Lists.newArrayList();
        for (SCMCheckoutStrategyDescriptor d : all()) {
            if (d.isApplicable(p))
                r.add(d);
        }
        return r;
    }
                
    

}
