/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.security.ACL;
import hudson.util.HttpResponses;
import hudson.util.Secret;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenStats;
import jenkins.security.apitoken.ApiTokenStore;
import jenkins.security.apitoken.TokenUuidAndPlainValue;
import jenkins.util.SystemProperties;
import net.jcip.annotations.Immutable;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Remembers the API token for this user, that can be used like a password to login.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see ApiTokenFilter
 * @since 1.426
 */
public class ApiTokenProperty extends UserProperty {
    private static final Logger LOGGER = Logger.getLogger(ApiTokenProperty.class.getName());

    /**
     * If enabled, the users with {@link Jenkins#ADMINISTER} permissions can view legacy tokens for
     * other users.<p>
     * Disabled by default due to the security reasons.<p>
     * If enabled, it restores the original Jenkins behavior (SECURITY-200).
     *
     * @since 1.638
     */
    private static /* not final */ boolean SHOW_LEGACY_TOKEN_TO_ADMINS =
            SystemProperties.getBoolean(ApiTokenProperty.class.getName() + ".showTokenToAdmins");

    /**
     * If enabled, the users with {@link Jenkins#ADMINISTER} permissions can generate new tokens for
     * other users. Normally a user can only generate tokens for himself.<p>
     * Take care that only the creator of a token will have the plain value as it's only stored as an hash in the system.<p>
     * Disabled by default due to the security reasons.
     * It's the version of {@link #SHOW_LEGACY_TOKEN_TO_ADMINS} for the new API Token system (SECURITY-200).
     *
     * @since 2.129
     */
    private static /* not final */ boolean ADMIN_CAN_GENERATE_NEW_TOKENS =
            SystemProperties.getBoolean(ApiTokenProperty.class.getName() + ".adminCanGenerateNewTokens");

    private volatile Secret apiToken;
    private ApiTokenStore tokenStore;

    /**
     * Store the usage information of the different token for this user
     * The save operation can be toggled by using {@link ApiTokenPropertyConfiguration#setUsageStatisticsEnabled(boolean)}
     * The information are stored in a separate file to avoid problem with some configuration synchronization tools
     */
    private transient ApiTokenStats tokenStats;

    @DataBoundConstructor
    public ApiTokenProperty() {
    }

    @Override
    protected void setUser(User u) {
        super.setUser(u);

        if (this.tokenStore == null) {
            this.tokenStore = new ApiTokenStore();
        }
        if (this.tokenStats == null) {
            this.tokenStats = ApiTokenStats.load(user);
        }
        if (this.apiToken != null) {
            this.tokenStore.regenerateTokenFromLegacyIfRequired(this.apiToken);
        }
    }

    /**
     * We don't let the external code set the API token,
     * but for the initial value of the token we need to compute the seed by ourselves.
     */
    /*package*/ ApiTokenProperty(@CheckForNull String seed) {
        if (seed != null) {
            apiToken = Secret.fromString(seed);
        }
    }

    /**
     * Gets the API token.
     * The method performs security checks since 1.638. Only the current user and SYSTEM may see it.
     * Users with {@link Jenkins#ADMINISTER} may be allowed to do it using {@link #SHOW_LEGACY_TOKEN_TO_ADMINS}.
     *
     * @return API Token. Never null, but may be {@link Messages#ApiTokenProperty_ChangeToken_TokenIsHidden()}
     *         if the user has no appropriate permissions.
     * @since 1.426, and since 1.638 the method performs security checks
     */
    @NonNull
    public String getApiToken() {
        LOGGER.log(Level.FINE, "Deprecated usage of getApiToken");
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "Deprecated usage of getApiToken (trace)", new Exception());
        }
        return hasPermissionToSeeToken()
                ? getApiTokenInsecure()
                : Messages.ApiTokenProperty_ChangeToken_TokenIsHidden();
    }

    /**
     * Determine if the legacy token is still present
     */
    @Restricted(NoExternalUse.class)
    public boolean hasLegacyToken() {
        return apiToken != null;
    }

    @NonNull
    @Restricted(NoExternalUse.class)
    /*package*/ String getApiTokenInsecure() {
        if (apiToken == null) {
            return Messages.ApiTokenProperty_NoLegacyToken();
        }

        String p = apiToken.getPlainText();
        if (p.equals(Util.getDigestOf(Jenkins.get().getSecretKey() + ":" + user.getId()))) {
            // if the current token is the initial value created by pre SECURITY-49 Jenkins, we can't use that.
            // force using the newer value
            apiToken = Secret.fromString(p = API_KEY_SEED.mac(user.getId()));
        }
        return Util.getDigestOf(p);
    }

    public boolean matchesPassword(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        ApiTokenStore.HashedToken matchingToken = tokenStore.findMatchingToken(token);
        if (matchingToken == null) {
            return false;
        }

        tokenStats.updateUsageForId(matchingToken.getUuid());

        return true;
    }

    /**
     * Only for legacy token
     */
    private boolean hasPermissionToSeeToken() {
        // Administrators can do whatever they want
        return canCurrentUserControlObject(SHOW_LEGACY_TOKEN_TO_ADMINS, user);
    }

    private static boolean canCurrentUserControlObject(boolean trustAdmins, User propertyOwner) {
        if (trustAdmins && Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return true;
        }

        User current = User.current();
        if (current == null) { // Anonymous
            return false;
        }

        // SYSTEM user is always eligible to see tokens
        if (Jenkins.getAuthentication2().equals(ACL.SYSTEM2)) {
            return true;
        }

        return User.idStrategy().equals(propertyOwner.getId(), current.getId());
    }

    // only for Jelly
    @Restricted(NoExternalUse.class)
    public Collection<TokenInfoAndStats> getTokenList() {
        return tokenStore.getTokenListSortedByName()
                .stream()
                .map(token -> {
                    ApiTokenStats.SingleTokenStats stats = tokenStats.findTokenStatsById(token.getUuid());
                    return new TokenInfoAndStats(token, stats);
                })
                .collect(Collectors.toList());
    }

    // only for Jelly
    @Immutable
    @Restricted(NoExternalUse.class)
    public static class TokenInfoAndStats {
        public final String uuid;
        public final String name;
        public final Date creationDate;
        public final long numDaysCreation;
        public final boolean isLegacy;

        public final int useCounter;
        public final Date lastUseDate;
        public final long numDaysUse;

        public TokenInfoAndStats(@NonNull ApiTokenStore.HashedToken token, @NonNull ApiTokenStats.SingleTokenStats stats) {
            this.uuid = token.getUuid();
            this.name = token.getName();
            this.creationDate = token.getCreationDate();
            this.numDaysCreation = token.getNumDaysCreation();
            this.isLegacy = token.isLegacy();

            this.useCounter = stats.getUseCounter();
            this.lastUseDate = stats.getLastUseDate();
            this.numDaysUse = stats.getNumDaysUse();
        }

        public String createdDaysAgo() {
            LocalDate c = creationDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate now = LocalDate.now(ZoneId.systemDefault());
            Period period = Period.between(c, now);
            if (period.getYears() > 1) {
                return Messages.ApiTokenProperty_createdYearsAgo(period.getYears());
            }
            if (period.getYears() == 1) {
                return Messages.ApiTokenProperty_createdAYearAgo();
            }
            if (period.getMonths() > 1) {
                return Messages.ApiTokenProperty_createdMonthsAgo(period.getMonths());
            }
            if (period.getMonths() == 1) {
                return Messages.ApiTokenProperty_createdAMonthAgo();
            }
            if (period.getDays() > 14) {
                return Messages.ApiTokenProperty_createdWeeksAgo(period.getDays() / 7);
            }
            if (period.getDays() >= 7) {
                return Messages.ApiTokenProperty_createdAWeekAgo();
            }
            if (period.getDays() == 0) {
                return Messages.ApiTokenProperty_createdToday();
            }
            if (period.getDays() == 1) {
                return Messages.ApiTokenProperty_createdYesterday();
            }
            return Messages.ApiTokenProperty_createdDaysAgo(period.getDays());
        }

        public String lastUsedDaysAgo() {
            LocalDate lu = lastUseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate now = LocalDate.now(ZoneId.systemDefault());
            Period period = Period.between(lu, now);
            String used = Messages.ApiTokenProperty_usedMultipleTimes(useCounter);
            if (useCounter == 1) {
                used = Messages.ApiTokenProperty_usedOneTime();
            }
            if (period.getYears() > 1) {
                return used + " " + Messages.ApiTokenProperty_lastUsedYearsAgo(period.getYears());
            }
            if (period.getYears() == 1) {
                return used + " " + Messages.ApiTokenProperty_lastUsedAYearAgo();
            }
            if (period.getMonths() > 1) {
                return used + " " + Messages.ApiTokenProperty_lastUsedMonthsAgo(period.getMonths());
            }
            if (period.getMonths() == 1) {
                return used + " " + Messages.ApiTokenProperty_lastUsedAMonthAgo();
            }
            if (period.getDays() > 14) {
                return used + " " + Messages.ApiTokenProperty_lastUsedWeeksAgo(period.getDays() / 7);
            }
            if (period.getDays() >= 7) {
                return used + " " + Messages.ApiTokenProperty_lastUsedAWeekAgo();
            }
            if (period.getDays() == 0) {
                return used + " " + Messages.ApiTokenProperty_lastUsedToday();
            }
            if (period.getDays() == 1) {
                return used + " " + Messages.ApiTokenProperty_lastUsedYesterday();
            }
            return used + " " + Messages.ApiTokenProperty_lastUsedDaysAgo(period.getDays());
        }
    }

    /**
     * Allow user to rename tokens
     */
    @Override
    public UserProperty reconfigure(StaplerRequest2 req, @CheckForNull JSONObject form) throws FormException {
        if (form == null) {
            return this;
        }

        Object tokenStoreData = form.get("tokenStore");
        Map<String, JSONObject> tokenStoreTypedData = convertToTokenMap(tokenStoreData);
        this.tokenStore.reconfigure(tokenStoreTypedData);
        return this;
    }

    private Map<String, JSONObject> convertToTokenMap(Object tokenStoreData) {
        if (tokenStoreData == null) {
            // in case there are no token
            return Collections.emptyMap();
        } else if (tokenStoreData instanceof JSONObject) {
            // in case there is only one token
            JSONObject singleTokenData = (JSONObject) tokenStoreData;
            Map<String, JSONObject> result = new HashMap<>();
            addJSONTokenIntoMap(result, singleTokenData);
            return result;
        } else if (tokenStoreData instanceof JSONArray) {
            // in case there are multiple tokens
            JSONArray tokenArray = (JSONArray) tokenStoreData;
            Map<String, JSONObject> result = new HashMap<>();
            for (int i = 0; i < tokenArray.size(); i++) {
                JSONObject tokenData = tokenArray.getJSONObject(i);
                addJSONTokenIntoMap(result, tokenData);
            }
            return result;
        }

        throw HttpResponses.error(400, "Unexpected class received for the token store information");
    }

    private void addJSONTokenIntoMap(Map<String, JSONObject> tokenMap, JSONObject tokenData) {
        String uuid = tokenData.getString("tokenUuid");
        tokenMap.put(uuid, tokenData);
    }

    /**
     * Only usable if the user still has the legacy API token.
     * @deprecated Each token can be revoked now and new tokens can be requested without altering existing ones.
     */
    @Deprecated
    public void changeApiToken() throws IOException {
        // just to keep the same level of security
        user.checkPermission(Jenkins.ADMINISTER);

        LOGGER.log(Level.FINE, "Deprecated usage of changeApiToken");

        ApiTokenStore.HashedToken existingLegacyToken = tokenStore.getLegacyToken();
        _changeApiToken();
        tokenStore.regenerateTokenFromLegacy(apiToken);

        if (existingLegacyToken != null) {
            tokenStats.removeId(existingLegacyToken.getUuid());
        }
        user.save();
    }

    @Deprecated
    private void _changeApiToken() {
        byte[] random = new byte[16];   // 16x8=128bit worth of randomness, since we use md5 digest as the API token
        RANDOM.nextBytes(random);
        apiToken = Secret.fromString(Util.toHexString(random));
    }

    /**
     * Does not revoke the token stored in the store
     */
    @Restricted(NoExternalUse.class)
    public void deleteApiToken() {
        this.apiToken = null;
    }

    @Restricted(NoExternalUse.class)
    public ApiTokenStore getTokenStore() {
        return tokenStore;
    }

    @Restricted(NoExternalUse.class)
    public ApiTokenStats getTokenStats() {
        return tokenStats;
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public @NonNull String addFixedNewToken(@NonNull String name, @NonNull String tokenPlainValue) throws IOException {
        String tokenUuid = this.tokenStore.addFixedNewToken(name, tokenPlainValue);
        user.save();
        return tokenUuid;
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public @NonNull TokenUuidAndPlainValue generateNewToken(@NonNull String name) throws IOException {
        TokenUuidAndPlainValue tokenUuidAndPlainValue = tokenStore.generateNewToken(name);
        user.save();
        return tokenUuidAndPlainValue;
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public void revokeAllTokens() throws IOException {
        tokenStats.removeAll();
        tokenStore.revokeAllTokens();
        user.save();
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public void revokeAllTokensExceptOne(@NonNull String tokenUuid) throws IOException {
        tokenStats.removeAllExcept(tokenUuid);
        tokenStore.revokeAllTokensExcept(tokenUuid);
        user.save();
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public void revokeToken(@NonNull String tokenUuid) throws IOException {
        ApiTokenStore.HashedToken revoked = tokenStore.revokeToken(tokenUuid);
        if (revoked != null) {
            if (revoked.isLegacy()) {
                // if the user revoked the API Token, we can delete it
                apiToken = null;
            }
            tokenStats.removeId(revoked.getUuid());
            user.save();
        }
    }

    @Extension
    @Symbol("apiToken")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ApiTokenProperty_DisplayName();
        }

        @Restricted(NoExternalUse.class) // Jelly use
        public String getNoLegacyToken() {
            return Messages.ApiTokenProperty_NoLegacyToken();
        }

        /**
         * New approach:
         * API Token are generated only when a user request a new one. The value is randomly generated
         * without any link to the user and only displayed to him the first time.
         * We only store the hash for future comparisons.
         *
         * Legacy approach:
         * When we are creating a default {@link ApiTokenProperty} for User,
         * we need to make sure it yields the same value for the same user,
         * because there's no guarantee that the property is saved.
         *
         * But we also need to make sure that an attacker won't be able to guess
         * the initial API token value. So we take the seed by hashing the secret + user ID.
         */
        @Override
        public ApiTokenProperty newInstance(User user) {
            if (!ApiTokenPropertyConfiguration.get().isTokenGenerationOnCreationEnabled()) {
                return forceNewInstance(user, false);
            }

            return forceNewInstance(user, true);
        }

        private ApiTokenProperty forceNewInstance(User user, boolean withLegacyToken) {
            if (withLegacyToken) {
                return new ApiTokenProperty(API_KEY_SEED.mac(user.getId()));
            } else {
                return new ApiTokenProperty(null);
            }
        }

        // for Jelly view
        @Restricted(NoExternalUse.class)
        public boolean isStatisticsEnabled() {
            return ApiTokenPropertyConfiguration.get().isUsageStatisticsEnabled();
        }

        // for Jelly view
        @Restricted(NoExternalUse.class)
        public boolean mustDisplayLegacyApiToken(User propertyOwner) {
            ApiTokenProperty property = propertyOwner.getProperty(ApiTokenProperty.class);
            if (property != null && property.apiToken != null) {
                return true;
            }
            return ApiTokenPropertyConfiguration.get().isCreationOfLegacyTokenEnabled();
        }

        // for Jelly view
        @Restricted(NoExternalUse.class)
        public boolean hasCurrentUserRightToGenerateNewToken(User propertyOwner) {
            return canCurrentUserControlObject(ADMIN_CAN_GENERATE_NEW_TOKENS, propertyOwner);
        }

        /**
         * @deprecated use {@link #doGenerateNewToken(User, String)} instead
         */
        @Deprecated
        @RequirePOST
        public HttpResponse doChangeToken(@AncestorInPath User u, StaplerResponse rsp) throws IOException {
            // you are the user or you have ADMINISTER permission
            u.checkPermission(Jenkins.ADMINISTER);

            LOGGER.log(Level.FINE, "Deprecated action /changeToken used, consider using /generateNewToken instead");

            if (!mustDisplayLegacyApiToken(u)) {
                // user does not have legacy token and the capability to create one without an existing one is disabled
                return HttpResponses.html(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed());
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                p = forceNewInstance(u, true);
                p.setUser(u);
                u.addProperty(p);
            } else {
                // even if the user does not have legacy token, this method let some legacy system to regenerate one
                p.changeApiToken();
            }

            rsp.setHeader("script", "document.getElementById('apiToken').value='" + p.getApiToken() + "'");
            return HttpResponses.html(p.hasPermissionToSeeToken()
                    ? Messages.ApiTokenProperty_ChangeToken_Success()
                    : Messages.ApiTokenProperty_ChangeToken_SuccessHidden());
        }

        @RequirePOST
        public HttpResponse doGenerateNewToken(@AncestorInPath User u, @QueryParameter String newTokenName) throws IOException {
            if (!hasCurrentUserRightToGenerateNewToken(u)) {
                return HttpResponses.forbidden();
            }

            final String tokenName;
            if (newTokenName == null || newTokenName.isBlank()) {
                tokenName = Messages.Token_Created_on(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
            } else {
                tokenName = newTokenName;
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                p = forceNewInstance(u, false);
                u.addProperty(p);
            }

            TokenUuidAndPlainValue tokenUuidAndPlainValue = p.generateNewToken(tokenName);

            Map<String, String> data = new HashMap<>();
            data.put("tokenUuid", tokenUuidAndPlainValue.tokenUuid);
            data.put("tokenName", tokenName);
            data.put("tokenValue", tokenUuidAndPlainValue.plainValue);
            return HttpResponses.okJSON(data);
        }

        /**
         * This method is dangerous and should not be used without caution.
         * The token passed here could have been tracked by different network system during its trip.
         * It is recommended to revoke this token after the generation of a new one.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        public HttpResponse doAddFixedToken(@AncestorInPath User u,
                                            @QueryParameter String newTokenName,
                                            @QueryParameter String newTokenPlainValue) throws IOException {
            if (!hasCurrentUserRightToGenerateNewToken(u)) {
                return HttpResponses.forbidden();
            }

            final String tokenName;
            if (newTokenName == null || newTokenName.isBlank()) {
                tokenName = String.format("Token created on %s", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
            } else {
                tokenName = newTokenName;
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                p = forceNewInstance(u, false);
                u.addProperty(p);
            }

            String tokenUuid = p.tokenStore.addFixedNewToken(tokenName, newTokenPlainValue);
            u.save();

            Map<String, String> data = new HashMap<>();
            data.put("tokenUuid", tokenUuid);
            data.put("tokenName", tokenName);
            return HttpResponses.okJSON(data);
        }

        @RequirePOST
        public HttpResponse doRename(@AncestorInPath User u,
                                     @QueryParameter String tokenUuid, @QueryParameter String newName) throws IOException {
            // only current user + administrator can rename token
            u.checkPermission(Jenkins.ADMINISTER);

            if (newName == null || newName.isBlank()) {
                return HttpResponses.errorJSON("The name cannot be empty");
            }
            if (tokenUuid == null || tokenUuid.isBlank()) {
                // using the web UI this should not occur
                return HttpResponses.errorJSON("The tokenUuid cannot be empty");
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorJSON("The user does not have any ApiToken yet, try generating one before.");
            }

            boolean renameOk = p.tokenStore.renameToken(tokenUuid, newName);
            if (!renameOk) {
                // that could potentially happen if the token is removed from another page
                // between your page loaded and your action
                return HttpResponses.errorJSON("No token found, try refreshing the page");
            }

            u.save();

            return HttpResponses.okJSON();
        }

        @RequirePOST
        public HttpResponse doRevoke(@AncestorInPath User u,
                                     @QueryParameter String tokenUuid) throws IOException {
            // only current user + administrator can revoke token
            u.checkPermission(Jenkins.ADMINISTER);

            if (tokenUuid == null || tokenUuid.isBlank()) {
                // using the web UI this should not occur
                return HttpResponses.errorWithoutStack(400, "The tokenUuid cannot be empty");
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorWithoutStack(400, "The user does not have any ApiToken yet, try generating one before.");
            }

            p.revokeToken(tokenUuid);

            return HttpResponses.ok();
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public HttpResponse doRevokeAll(@AncestorInPath User u) throws IOException {
            // only current user + administrator can revoke token
            u.checkPermission(Jenkins.ADMINISTER);

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorWithoutStack(400, "The user does not have any ApiToken yet, try generating one before.");
            }

            p.revokeAllTokens();

            return HttpResponses.ok();
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public HttpResponse doRevokeAllExcept(@AncestorInPath User u,
                                              @QueryParameter String tokenUuid) throws IOException {
            // only current user + administrator can revoke token
            u.checkPermission(Jenkins.ADMINISTER);

            if (tokenUuid == null || tokenUuid.isBlank()) {
                // using the web UI this should not occur
                return HttpResponses.errorWithoutStack(400, "The tokenUuid cannot be empty");
            }

            ApiTokenProperty p = u.getProperty(ApiTokenProperty.class);
            if (p == null) {
                return HttpResponses.errorWithoutStack(400, "The user does not have any ApiToken yet, try generating one before.");
            }

            p.revokeAllTokensExceptOne(tokenUuid);

            return HttpResponses.ok();
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Security.class);
        }
    }

    /**
     * @deprecated Only used for legacy API Token generation and change. After that token is revoked, it will be useless.
     */
    @Deprecated
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * We don't want an API key that's too long, so cut the length to 16 (which produces 32-letter MAC code in hexdump)
     * @deprecated only used for the migration of previous data
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static final HMACConfidentialKey API_KEY_SEED = new HMACConfidentialKey(ApiTokenProperty.class, "seed", 16);
}
