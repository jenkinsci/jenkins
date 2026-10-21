package jenkins.security.csp.winstoneResponseHeaderLengthTest;

import hudson.Extension;
import jenkins.model.Jenkins;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.Directive;

public class ContributorImpl implements Contributor {
    private int count = 0;

    @Override
    public void apply(CspBuilder cspBuilder) {
        count++;
        for (int i = 0; i < count; i++) {
            cspBuilder.add(Directive.IMG_SRC, "img" + i + ".example.com");
        }
    }

    @Extension
    public static ContributorImpl getInstance() {
        // Only load this extension if it's in the synthetic plugin, otherwise it will affect other tests
        if (Jenkins.get().getPluginManager().whichPlugin(ContributorImpl.class) == null) {
            return null;
        }
        return new ContributorImpl();
    }
}
