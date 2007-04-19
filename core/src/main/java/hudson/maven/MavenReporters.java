package hudson.maven;

import hudson.model.Descriptor;
import hudson.maven.reporters.MavenArtifactArchiver;
import hudson.maven.reporters.MavenFingerprinter;
import hudson.maven.reporters.MavenJavadocArchiver;
import hudson.maven.reporters.SurefireArchiver;
import hudson.maven.reporters.MavenMailer;
import hudson.maven.reporters.BuildInfoRecorder;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Kohsuke Kawaguchi
 * @see MavenReporter
 */
public final class MavenReporters {
    /**
     * List of all installed {@link MavenReporter}s.
     */
    public static final List<MavenReporterDescriptor> LIST = Descriptor.toList(
        MavenArtifactArchiver.DescriptorImpl.DESCRIPTOR,
        MavenFingerprinter.DescriptorImpl.DESCRIPTOR,
        MavenJavadocArchiver.DescriptorImpl.DESCRIPTOR,
        SurefireArchiver.DescriptorImpl.DESCRIPTOR,
        MavenMailer.DescriptorImpl.DESCRIPTOR,
        BuildInfoRecorder.DescriptorImpl.DESCRIPTOR
    );

    /**
     * Gets the subset of {@link #LIST} that has configuration screen.
     */
    public static List<MavenReporterDescriptor> getConfigurableList() {
        List<MavenReporterDescriptor> r = new ArrayList<MavenReporterDescriptor>();
        for (MavenReporterDescriptor d : LIST) {
            if(d.hasConfigScreen())
                r.add(d);
        }
        return r;
    }
}
