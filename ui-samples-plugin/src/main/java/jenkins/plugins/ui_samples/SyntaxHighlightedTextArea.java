package jenkins.plugins.ui_samples;

import hudson.Extension;

import java.util.Arrays;
import java.util.List;

/**
 * Syntax-highlighted text area (powered by CodeMirror).
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SyntaxHighlightedTextArea extends UISample {
    @Override
    public String getDescription() {
        return "Syntax-highlighted text area powered by CodeMirror";
    }

    public List<SourceFile> getSourceFiles() {
        // TODO: generate this from index
        return Arrays.asList(new SourceFile(getClass().getSimpleName() + ".java"),
                new SourceFile("index.groovy"));
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }
}
