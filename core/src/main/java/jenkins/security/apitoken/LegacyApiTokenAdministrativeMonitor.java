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
package jenkins.security.apitoken;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import hudson.util.HttpResponses;
import jenkins.security.ApiTokenProperty;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.json.JsonBody;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Monitor the list of users that still have legacy token
 */
@Extension
@Symbol("legacyApiTokenUsage")
@Restricted(NoExternalUse.class)
public class LegacyApiTokenAdministrativeMonitor extends AdministrativeMonitor {
    private static final Logger LOGGER = Logger.getLogger(LegacyApiTokenAdministrativeMonitor.class.getName());
    
    public LegacyApiTokenAdministrativeMonitor() {
        super("legacyApiToken");
    }
    
    @Override
    public String getDisplayName() {
        return Messages.LegacyApiTokenAdministrativeMonitor_displayName();
    }
    
    @Override
    public boolean isActivated() {
        return User.getAll().stream()
                .anyMatch(user -> {
                    ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
                    return (apiTokenProperty != null && apiTokenProperty.hasLegacyToken());
                });
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    public HttpResponse doIndex() throws IOException {
        return new HttpRedirect("manage");
    }
    
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public List<User> getImpactedUserList() {
        return User.getAll().stream()
                .filter(user -> {
                    ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
                    return (apiTokenProperty != null && apiTokenProperty.hasLegacyToken());
                })
                .collect(Collectors.toList());
    }
    
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public @Nullable ApiTokenStore.HashedToken getLegacyTokenOf(@NonNull User user) {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        return apiTokenProperty.getTokenStore().getLegacyToken();
    }
    
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public @Nullable ApiTokenProperty.TokenInfoAndStats getLegacyStatsOf(@NonNull User user, ApiTokenStore.HashedToken legacyToken) {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        if (legacyToken != null) {
            ApiTokenStats.SingleTokenStats legacyStats = apiTokenProperty.getTokenStats().findTokenStatsById(legacyToken.getUuid());
            return new ApiTokenProperty.TokenInfoAndStats(legacyToken, legacyStats);
        }
        
        // in case the legacy token was revoked during the request
        return null;
    }
    
    /**
     * Determine if the user has at least one "new" token that was created after the last use of the legacy token
     */
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public boolean hasFreshToken(@NonNull User user, ApiTokenProperty.TokenInfoAndStats legacyStats) {
        if (legacyStats == null) {
            return false;
        }
        
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        
        return apiTokenProperty.getTokenList().stream()
                .filter(token -> !token.isLegacy)
                .anyMatch(token -> {
                    Date creationDate = token.creationDate;
                    Date lastUseDate = legacyStats.lastUseDate;
                    if (lastUseDate == null) {
                        lastUseDate = legacyStats.creationDate;
                    }
                    return creationDate != null && lastUseDate != null && creationDate.after(lastUseDate);
                });
    }
    
    /**
     * Determine if the user has at least one "new" token that was used after the last use of the legacy token
     */
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public boolean hasMoreRecentlyUsedToken(@NonNull User user, ApiTokenProperty.TokenInfoAndStats legacyStats) {
        if (legacyStats == null) {
            return false;
        }
        
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        
        return apiTokenProperty.getTokenList().stream()
                .filter(token -> !token.isLegacy)
                .anyMatch(token -> {
                    Date currentLastUseDate = token.lastUseDate;
                    Date legacyLastUseDate = legacyStats.lastUseDate;
                    if (legacyLastUseDate == null) {
                        legacyLastUseDate = legacyStats.creationDate;
                    }
                    return currentLastUseDate != null && legacyLastUseDate != null && currentLastUseDate.after(legacyLastUseDate);
                });
    }
    
    @RequirePOST
    public HttpResponse doRevokeAllSelected(@JsonBody RevokeAllSelectedModel content) throws IOException {
        for (RevokeAllSelectedUserAndUuid value : content.values) {
            if (value.userId == null) {
                // special case not managed by JSONObject
                value.userId = "null";
            }
            User user = User.getById(value.userId, false);
            if (user == null) {
                LOGGER.log(Level.INFO, "User not found id={0}", value.userId);
            } else {
                ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
                if (apiTokenProperty == null) {
                    LOGGER.log(Level.INFO, "User without apiTokenProperty found id={0}", value.userId);
                } else {
                    ApiTokenStore.HashedToken revokedToken = apiTokenProperty.getTokenStore().revokeToken(value.uuid);
                    if (revokedToken == null) {
                        LOGGER.log(Level.INFO, "User without selected token id={0}, tokenUuid={1}", new Object[]{value.userId, value.uuid});
                    } else {
                        apiTokenProperty.deleteApiToken();
                        user.save();
                        LOGGER.log(Level.INFO, "Revocation success for user id={0}, tokenUuid={1}", new Object[]{value.userId, value.uuid});
                    }
                }
            }
        }
        return HttpResponses.ok();
    }
    
    @Restricted(NoExternalUse.class)
    public static final class RevokeAllSelectedModel {
        public RevokeAllSelectedUserAndUuid[] values;
    }
    
    @Restricted(NoExternalUse.class)
    public static final class RevokeAllSelectedUserAndUuid {
        public String userId;
        public String uuid;
    }
}
