package jenkins.plugins.ui_samples;

import hudson.Extension;

import java.util.Arrays;
import java.util.List;

/**
 * Define portions of view fragments in separate methods/classes to improve reuse.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ModularizeViewScript extends UISample {
    @Override
    public String getDescription() {
        return "Define portions of view fragments in separate methods/classes to improve reuse";
    }

    public List<SourceFile> getSourceFiles() {
        // TODO: generate this from index
        return Arrays.asList(new SourceFile("index.groovy"));
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}
