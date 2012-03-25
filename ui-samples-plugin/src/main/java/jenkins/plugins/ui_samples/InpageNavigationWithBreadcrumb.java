package jenkins.plugins.ui_samples;

import hudson.Extension;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class InpageNavigationWithBreadcrumb extends UISample {
    @Override
    public String getDescription() {
        return "Adds in-page navigation with extra breadcrumb";
    }

    public List<SourceFile> getSourceFiles() {
        // TODO: generate this from index
        return Arrays.asList(
                new SourceFile("index.groovy"),
                new SourceFile("header.groovy"));
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}


