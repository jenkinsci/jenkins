package jenkins.security.csp.winstoneResponseHeaderLengthTest;

import hudson.Extension;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.Directive;

@Extension
public class ContributorImpl implements Contributor {
    private int count = 0;

    @Override
    public void apply(CspBuilder cspBuilder) {
        count++;
        for (int i = 0; i < count; i++) {
            cspBuilder.add(Directive.IMG_SRC, "img" + i + ".example.com");
        }
    }
}
