package jenkins.plugins.ui_samples;

import hudson.Extension;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CopyButton extends UISample {
    @Override
    public String getDescription() {
        return "A small button to copy text into a clipboard";
    }

    public List<SourceFile> getSourceFiles() {
        // TODO: generate this from index
        return Arrays.asList(
                new SourceFile("index.groovy"));
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}


