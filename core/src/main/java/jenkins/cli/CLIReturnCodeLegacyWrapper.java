package jenkins.cli;

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
