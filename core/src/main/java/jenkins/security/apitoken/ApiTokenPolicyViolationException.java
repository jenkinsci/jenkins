/*
 * The MIT License
 *
 * Copyright (c) 2026, Aitor Lopez
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

package jenkins.security.apitoken;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.LocalDate;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Thrown when a token is refused because it would break the rules that the administrator configured for API tokens, see
 * {@link ApiTokenLifetimePolicy}. It extends {@link IllegalArgumentException} like the exceptions for a malformed token
 * value do, and {@link #getReason()} tells them apart. Only Jenkins core throws it: callers catch it, and plugins do not
 * throw it, as its constructor is not for external use.
 * <p>
 * What is refused, and when:
 * <ul>
 * <li>{@link Reason#NEVER_EXPIRES} and {@link Reason#OVER_MAXIMUM}: a token that never expires, or that expires later than
 * today plus the maximum lifetime of API tokens. {@link ApiTokenStore#generateNewToken(String, LocalDate)},
 * {@link ApiTokenStore#addFixedNewToken(String, String, LocalDate)} and the methods of
 * {@link jenkins.security.ApiTokenProperty} that call them throw these <em>only</em> while the strict mode is in effect,
 * see {@link ApiTokenLifetimePolicy#isStrict(LocalDate)}: otherwise they do not apply the maximum, and every expiration is
 * accepted. While it is in effect {@link jenkins.security.ApiTokenProperty#changeApiToken()} throws
 * {@link Reason#NEVER_EXPIRES}, because the legacy token that it creates never expires.
 * <li>{@link Reason#SECRET_ALREADY_REGISTERED}: a secret that the store of the user holds already, through an entry that has
 * expired or not. It is thrown by {@link ApiTokenStore#addFixedNewTokenRejectingKnownSecret}, which the web endpoint that
 * registers a fixed token calls while the maximum applies to the owner of the token, and by
 * {@code ApiTokenStore.addFixedNewToken} too while the strict mode is in effect. It is not thrown by the methods that
 * generate a random token, whose secret is not checked.
 * </ul>
 * The checks run in a fixed order, which decides the reason: the format of a fixed token first, and a malformed one is not a
 * policy violation, then the expiration, then the secret. A request that is over the maximum and has a known secret is
 * refused for the maximum.
 * <p>
 * Strict mode and the token of the initial administrator: {@code jenkins.install.SetupWizard} creates a token that never
 * expires, so it fails with this exception if the strict mode is in effect when it runs. That takes a configuration that
 * was in place before the wizard ran, for example a configuration as code file or a configuration file that an installer
 * provides, as the strict mode is off by default. On the random path
 * ({@code jenkins.install.SetupWizard.adminInitialApiToken=true}) the exception is wrapped in a {@link RuntimeException}
 * in the task that finalizes the set up of Jenkins, inside a change that is not saved, and the installation state is not
 * saved either: every restart takes the same path and fails again. Jenkins will not start until strict mode is switched off
 * or the system property {@code jenkins.install.SetupWizard.adminInitialApiToken} is not set. On the fixed-token path, where
 * the value of the token is given, the wizard only logs a WARNING and creates no token, and the start-up continues. Do not
 * combine the strict mode with that system property: create the token of the administrator by script once Jenkins has
 * started, with an expiration that respects the maximum. In the same way {@code JenkinsRule.createApiToken} and
 * provisioning scripts that give no expiration fail, but only on instances where the strict mode is in effect.
 * <p>
 * The message is fixed English text for logs and scripts. It contains no value that came from the caller, the secret
 * included. It is never shown in a web response: the endpoints choose a localized message from the reason.
 *
 * @since TODO
 */
public class ApiTokenPolicyViolationException extends IllegalArgumentException {

    /**
     * Which rule the token would break.
     *
     * @since TODO
     */
    public enum Reason {
        /**
         * The token would never expire, while the maximum lifetime of API tokens applies.
         */
        NEVER_EXPIRES("The token has no expiration, which is not allowed while the maximum lifetime of API tokens applies"),
        /**
         * The token would expire later than today plus the maximum lifetime of API tokens.
         */
        OVER_MAXIMUM("The expiration of the token is later than the maximum lifetime of API tokens allows"),
        /**
         * The secret of the token is held by an entry of the store of the same user, expired or not.
         */
        SECRET_ALREADY_REGISTERED("A token with this secret is already registered for this user");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    /**
     * Only for Jenkins core: this exception is thrown by the API token classes of core, not by plugins.
     */
    @Restricted(NoExternalUse.class)
    public ApiTokenPolicyViolationException(@NonNull Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    /**
     * The rule that the token would break.
     */
    @NonNull
    public Reason getReason() {
        return reason;
    }
}
