package jenkins.cli;

import hudson.cli.CLICommand;

/**
 * Should only be used to wrap legacy use of {@link CLICommand#run()} inside commands
 * @since TODO
 */
public class CLIReturnCodeLegacyWrapper implements CLIReturnCode {
	private final int legacyCode;

	public CLIReturnCodeLegacyWrapper(int legacyCode){
		this.legacyCode = legacyCode;
	}

	@Override
	public int getCode() {
		return legacyCode;
	}
}
