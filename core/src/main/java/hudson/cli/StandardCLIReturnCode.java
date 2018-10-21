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
package hudson.cli;

import org.jvnet.localizer.Localizable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Set of standard CLI return code
 * Jenkins standard exit codes from CLI:
 *  <dl>
 *   <dt>0</dt><dd>means everything went well.</dd>
 *   <dt>1</dt><dd>means further unspecified exception is thrown while performing the command.</dd>
 *   <dt>2</dt><dd>means CmdLineException is thrown while performing the command.</dd>
 *   <dt>3</dt><dd>means IllegalArgumentException is thrown while performing the command.</dd>
 *   <dt>4</dt><dd>means IllegalStateException is thrown while performing the command.</dd>
 *   <dt>5</dt><dd>means AbortException is thrown while performing the command.</dd>
 *   <dt>6</dt><dd>means AccessDeniedException is thrown while performing the command.</dd>
 *   <dt>7</dt><dd>means BadCredentialsException is thrown while performing the command.</dd>
 *   <dt>8-15</dt><dd>are reserved for future usage</dd>
 *   <dt>16+</dt><dd>mean a custom CLI exit error code (meaning defined by the CLI command itself)</dd>
 *  </dl>
 * 
 * @since TODO
 */
public enum StandardCLIReturnCode implements CLIReturnCode {
    /**
     * Everything went ok
     * HTTP equivalent: 2xx
     */
    OK(0, null),
    /**
     * Internal server error
     * HTTP equivalent: 500
     */
    UNKNOWN_ERROR_OCCURRED(1, Messages._StandardCLIReturnCode_UNKNOWN_ERROR_OCCURRED()),
    /**
     * Input cannot be decoded or are wrong
     * HTTP equivalent: 400
     */
    WRONG_CMD_PARAMETER(2, Messages._StandardCLIReturnCode_WRONG_CMD_PARAMETER()),
    /**
     * Wrong input arguments
     * HTTP equivalent: 400, 404
     * Example: job doesn't exist
     */
    ILLEGAL_ARGUMENT(3, Messages._StandardCLIReturnCode_ILLEGAL_ARGUMENT()),
    /**
     * Correct input but wrong state
     * HTTP equivalent: 400, 410
     * Example: build is already finished
     */
    ILLEGAL_STATE(4, Messages._StandardCLIReturnCode_ILLEGAL_STATE()),
    /**
     * Can't continue due to an other (rare) issue
     */
    ABORTED(5, Messages._StandardCLIReturnCode_ABORTED()),
    /**
     * User is authenticated but does not have sufficient permission
     * HTTP equivalent: 403
     */
    ACCESS_DENIED(6, Messages._StandardCLIReturnCode_ACCESS_DENIED()),
    /**
     * Credentials sent but are invalid
     * HTTP equivalent: 401
     */
    BAD_CREDENTIALS(7, Messages._StandardCLIReturnCode_BAD_CREDENTIALS()),

    ;

    private final int code;
    private final Localizable localizable;

    StandardCLIReturnCode(int code, @CheckForNull Localizable localizable){
        this.code = code;
        this.localizable = localizable;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @CheckForNull String getReason(@Nonnull Locale locale) {
        if (localizable != null) {
            return localizable.toString(locale);
        }

        return null;
    }

    @Override
    public String toString() {
        return "Standard: " + code + ", " + name();
    }
}
