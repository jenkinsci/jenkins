package jenkins.cli;

/**
 * Set of standard CLI return code
 * @since TODO
 */
public enum CLIReturnCodeStandard implements CLIReturnCode {
	/**
	 * Everything went ok
	 * HTTP equivalent: 2xx
 	 */
	OK(0),
	/**
	 * Internal server error
	 * HTTP equivalent: 500
 	 */
	UNKNOWN_ERROR_OCCURRED(1),
	/**
	 * Input cannot be decoded or are wrong
	 * HTTP equivalent: 400
 	 */
	WRONG_CMD_PARAMETER(2),
	/**
	 * Wrong input arguments
	 * HTTP equivalent: 400, 404
	 * Example: job doesn't exist
 	 */
	ILLEGAL_ARGUMENT(3),
	/**
	 * Correct input but wrong state
	 * HTTP equivalent: 400, 410
	 * Example: build is already finished
	 */
	ILLEGAL_STATE(4),
	/**
	 * Can't continue due to an other (rare) issue
	 */
	ABORTED(5),
	/**
	 * User is authenticated but does not have sufficient permission
	 * HTTP equivalent: 403
 	 */
	ACCESS_DENIED(6),
	/**
	 * Credentials sent but are invalid
	 * HTTP equivalent: 401
 	 */
	BAD_CREDENTIALS(7),

	;

	private final int code;

	CLIReturnCodeStandard(int code){
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
