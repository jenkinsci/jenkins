package jenkins.cli;

/**
 * Marker interface used by {@link hudson.cli.CLICommand} implementations to indicate that
 * they are accessible to requests that do not have the READ permission on {@link jenkins.model.Jenkins}.
 *
 * @since TODO
 */
public interface UnprotectedCLICommand {
}
