package hudson.maven;

import org.apache.maven.reporting.MavenReport;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;

/**
 * Information about {@link MavenReport} that was executed.
 *
 * <p>
 * Since {@link MavenReport} is always a {@link Mojo} (even though the interface
 * inheritance is not explicitly defined), this class extends from {@link MojoInfo}.
 *
 * <p>
 * This object provides additional convenience methods that only make sense for {@link MavenReport}.
 *
 * @author Kohsuke Kawaguchi
 * @see MojoInfo
 */
public final class MavenReportInfo extends MojoInfo {
    /**
     * The fully-populated {@link MavenReport} object. The same object as 
     * {@link #mojo} but in the right type. Never null.
     */
    public final MavenReport report;

    public MavenReportInfo(MojoExecution mojoExecution, MavenReport mojo, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) {
        super(mojoExecution, (Mojo)mojo, configuration, expressionEvaluator);
        this.report = mojo;
    }
}
