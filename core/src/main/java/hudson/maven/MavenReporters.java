package hudson.maven;

import hudson.model.Descriptor;
import hudson.maven.reporters.MavenArtifactArchiver;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 * @see MavenReporter
 */
public final class MavenReporters {
    /**
     * List of all installed {@link MavenReporter}s.
     */
    public static final List<MavenReporterDescriptor> LIST = Descriptor.<MavenReporterDescriptor>toList(
        MavenArtifactArchiver.DescriptorImpl.DESCRIPTOR
    );
}
