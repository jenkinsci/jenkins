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
 * Thrown by the {@link ApiTokenStore} when it refuses to store a token because that would break the rules that the
 * administrator configured for API tokens, see {@link ApiTokenLifetimePolicy}. It extends
 * {@link IllegalArgumentException} like the exceptions for a malformed token value do, and {@link #getReason()} tells
 * them apart.
 * <p>
 * For now it is thrown only by {@link ApiTokenStore#addFixedNewTokenRejectingKnownSecret}, which is restricted to core:
 * the web endpoint that registers a fixed token calls it while the maximum lifetime is enforced for the owner of the
 * token, and the exception is {@link Reason#SECRET_ALREADY_REGISTERED}: the secret is held by an entry of the store
 * already, whether that entry has expired or not. {@link ApiTokenStore#addFixedNewToken(String, String, LocalDate)} and the methods that generate
 * a random token never throw it.
 * <p>
 * The message is fixed English text for logs and scripts. It contains no value that came from the caller, the secret
 * included. It is never shown in a web response: the endpoint chooses a localized message from the reason.
 */
@Restricted(NoExternalUse.class)
public class ApiTokenPolicyViolationException extends IllegalArgumentException {

    /**
     * Which rule the token would break.
     */
    public enum Reason {
        /**
         * The token would not respect the maximum lifetime. Reserved for a strict mode that is not part of the store yet,
         * nothing throws it for now.
         */
        LIFETIME("The expiration of the token does not respect the maximum lifetime of API tokens"),
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
