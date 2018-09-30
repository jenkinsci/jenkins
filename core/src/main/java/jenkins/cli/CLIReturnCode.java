package jenkins.cli;

/**
 * Represent the return value of a CLI command.
 * Jenkins standard exit codes from CLI:
 * <dl>
 *  <dt>0</dt><dd>means everything went well.</dd>
 *  <dt>1</dt><dd>means further unspecified exception is thrown while performing the command.</dd>
 *  <dt>2</dt><dd>means CmdLineException is thrown while performing the command.</dd>
 *  <dt>3</dt><dd>means IllegalArgumentException is thrown while performing the command.</dd>
 *  <dt>4</dt><dd>means IllegalStateException is thrown while performing the command.</dd>
 *  <dt>5</dt><dd>means AbortException is thrown while performing the command.</dd>
 *  <dt>6</dt><dd>means AccessDeniedException is thrown while performing the command.</dd>
 *  <dt>7</dt><dd>means BadCredentialsException is thrown while performing the command.</dd>
 *  <dt>8-15</dt><dd>are reserved for future usage</dd>
 *  <dt>16+</dt><dd>mean a custom CLI exit error code (meaning defined by the CLI command itself)</dd>
 * </dl>
 *
 * Can be implemented by enum when you need to return custom code
 * Note: For details - see JENKINS-32273
 * @see CLIReturnCodeStandard
 * @since TODO
 */
public interface CLIReturnCode {
	/**
	 * @return The desired exit code that respect the contract described in {@link CLIReturnCode}
	 */
	int getCode();
}
