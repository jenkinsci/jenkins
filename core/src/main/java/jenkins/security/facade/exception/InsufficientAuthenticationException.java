/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jenkins.security.facade.exception;

/**
 * Thrown if an authentication request is rejected because the credentials are not sufficiently trusted.
 * <p><code>AccessDecisionVoter</code>s will typically throw this exception if they are dissatisfied with the
 * level of the authentication, such as if performed using a remember-me mechanism or anonymously. The commonly used
 * <code>ExceptionTranslationFilter</code> will thus cause the <code>AuthenticationEntryPoint</code>
 * to be called, allowing the principal to authenticate with a stronger level of authentication.</p>
 *
 * Copied from acegi-security
 */
public class InsufficientAuthenticationException extends org.acegisecurity.InsufficientAuthenticationException {
    /**
     * Constructs an <code>InsufficientAuthenticationException</code> with the
     * specified message.
     *
     * @param msg the detail message
     */
    public InsufficientAuthenticationException(String msg) {
        super(msg);
    }

    /**
     * Constructs an <code>InsufficientAuthenticationException</code> with the
     * specified message and root cause.
     *
     * @param msg the detail message
     * @param t root cause
     */
    public InsufficientAuthenticationException(String msg, Throwable t) {
        super(msg, t);
    }
}
