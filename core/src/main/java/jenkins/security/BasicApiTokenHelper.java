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

package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jenkins.model.GlobalConfiguration;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class BasicApiTokenHelper {
    public static @CheckForNull User isConnectingUsingApiToken(String username, String tokenValue) {
        TokenMatch match = findMatchingToken(username, tokenValue);
        return match == null ? null : match.user;
    }

    public static @CheckForNull TokenMatch findMatchingToken(String username, String tokenValue) {
        User user = User.getById(username, false);
        if (user == null) {
            ApiTokenPropertyConfiguration apiTokenConfiguration = GlobalConfiguration.all().getInstance(ApiTokenPropertyConfiguration.class);
            if (apiTokenConfiguration.isTokenGenerationOnCreationEnabled()) {
                String generatedTokenOnCreation = Util.getDigestOf(ApiTokenProperty.API_KEY_SEED.mac(username));
                boolean areTokenEqual = MessageDigest.isEqual(
                        generatedTokenOnCreation.getBytes(StandardCharsets.US_ASCII),
                        tokenValue.getBytes(StandardCharsets.US_ASCII)
                );
                if (areTokenEqual) {
                    // bootstrap token — no backing HashedToken, treated as unscoped
                    return new TokenMatch(User.getById(username, true), null);
                }
            }
        } else {
            ApiTokenProperty t = user.getProperty(ApiTokenProperty.class);
            if (t != null) {
                ApiTokenStore.HashedToken matched = t.findMatchingToken(tokenValue);
                if (matched != null) {
                    return new TokenMatch(user, matched);
                }
            }
        }

        return null;
    }

    public record TokenMatch(@NonNull User user, @CheckForNull ApiTokenStore.HashedToken token) {}
}
