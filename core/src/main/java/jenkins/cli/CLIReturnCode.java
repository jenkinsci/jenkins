/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
