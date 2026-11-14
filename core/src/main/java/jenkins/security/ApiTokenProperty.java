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
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Functions;
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
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenLifetimePolicy;
import jenkins.security.apitoken.ApiTokenPolicyViolationException;
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
        public final String expirationDate;
        public final boolean expired;
        public final boolean aboutToExpire;

        public TokenInfoAndStats(@NonNull ApiTokenStore.HashedToken token, @NonNull ApiTokenStats.SingleTokenStats stats) {
            this.uuid = token.getUuid();
            this.name = token.getName();
            this.creationDate = token.getCreationDate();
            this.numDaysCreation = token.getNumDaysCreation();
            this.isLegacy = token.isLegacy();
            this.expired = token.isExpired();
            this.aboutToExpire = token.isAboutToExpire();

            LocalDate expirationDate = token.getExpirationDate();
            if (expirationDate == null) {
                this.expirationDate = "never";
            } else {
                this.expirationDate = expirationDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                        .withLocale(Functions.getCurrentLocale()));
            }

            this.useCounter = stats.getUseCounter();
            this.lastUseDate = stats.getLastUseDate();
            this.numDaysUse = stats.getNumDaysUse();
        }

        /**
         * How long ago the token was created, for display.
         *
         * @return an empty string if the creation date is not known, as for a legacy token that was already there before
         *         creation dates were recorded
         */
        public String createdDaysAgo() {
            if (creationDate == null) {
                return "";
            }
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

        /**
         * How long ago the token was last used, for display.
         *
         * @return an empty string if the token was never used, or the usage statistics are not recorded
         */
        public String lastUsedDaysAgo() {
            if (lastUseDate == null) {
                return "";
            }
            LocalDate lu = lastUseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate now = LocalDate.now(ZoneId.systemDefault());
            Period period = Period.between(lu, now);
            if (period.getYears() > 1) {
                return Messages.ApiTokenProperty_lastUsedYearsAgo(period.getYears());
            }
            if (period.getYears() == 1) {
                return Messages.ApiTokenProperty_lastUsedAYearAgo();
            }
            if (period.getMonths() > 1) {
                return Messages.ApiTokenProperty_lastUsedMonthsAgo(period.getMonths());
            }
            if (period.getMonths() == 1) {
                return Messages.ApiTokenProperty_lastUsedAMonthAgo();
            }
            if (period.getDays() > 14) {
                return Messages.ApiTokenProperty_lastUsedWeeksAgo(period.getDays() / 7);
            }
            if (period.getDays() >= 7) {
                return Messages.ApiTokenProperty_lastUsedAWeekAgo();
            }
            if (period.getDays() == 0) {
                return Messages.ApiTokenProperty_lastUsedToday();
            }
            if (period.getDays() == 1) {
                return Messages.ApiTokenProperty_lastUsedYesterday();
            }
            return Messages.ApiTokenProperty_lastUsedDaysAgo(period.getDays());
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
        switch (tokenStoreData) {
            case null -> {
                // in case there are no token
                return Collections.emptyMap();
            }
            case JSONObject singleTokenData -> {
                // in case there is only one token
                Map<String, JSONObject> result = new HashMap<>();
                addJSONTokenIntoMap(result, singleTokenData);
                return result;
            }
            case JSONArray tokenArray -> {
                // in case there are multiple tokens
                Map<String, JSONObject> result = new HashMap<>();
                for (int i = 0; i < tokenArray.size(); i++) {
                    JSONObject tokenData = tokenArray.getJSONObject(i);
                    addJSONTokenIntoMap(result, tokenData);
                }
                return result;
            }
            default -> {
                throw HttpResponses.error(400, "Unexpected class received for the token store information");
            }
        }
    }

    private void addJSONTokenIntoMap(Map<String, JSONObject> tokenMap, JSONObject tokenData) {
        String uuid = tokenData.getString("tokenUuid");
        tokenMap.put(uuid, tokenData);
    }

    /**
     * Only usable if the user still has the legacy API token.
     * <p>
     * The legacy token that this creates never expires. The maximum lifetime of API tokens is not applied to it, unless the
     * strict mode is in effect (see {@link ApiTokenLifetimePolicy#isStrict}): then nothing is changed and
     * {@link ApiTokenPolicyViolationException} is thrown, with the reason
     * {@link ApiTokenPolicyViolationException.Reason#NEVER_EXPIRES}.
     *
     * @throws ApiTokenPolicyViolationException only while the strict mode is in effect
     * @deprecated Each token can be revoked now and new tokens can be requested without altering existing ones.
     */
    @Deprecated
    public void changeApiToken() throws IOException {
        // just to keep the same level of security
        user.checkPermission(Jenkins.ADMINISTER);

        LOGGER.log(Level.FINE, "Deprecated usage of changeApiToken");

        // The legacy token that is created has no expiration. This has to be decided before _changeApiToken() replaces the
        // secret in memory: after that, a refusal would leave a secret that does not match the stored hash.
        if (new ApiTokenLifetimePolicy().isStrict(LocalDate.now())) {
            throw new ApiTokenPolicyViolationException(ApiTokenPolicyViolationException.Reason.NEVER_EXPIRES);
        }

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

    /**
     * Essentially meant for scripting.
     * <p>
     * The token never expires, so this fails while the strict mode of the maximum lifetime of API tokens is in effect: see
     * {@link #addFixedNewToken(String, String, LocalDate)}.
     *
     * @throws ApiTokenPolicyViolationException only while the strict mode is in effect
     */
    @Restricted(Beta.class)
    public @NonNull String addFixedNewToken(@NonNull String name, @NonNull String tokenPlainValue) throws IOException {
        return addFixedNewToken(name, tokenPlainValue, null);
    }

    /**
     * Essentially meant for scripting.
     * <p>
     * Unlike the HTTP endpoint while the maximum is enforced for this user, this method neither applies the maximum
     * lifetime of API tokens nor refuses a secret that this user holds already: the secret is stored again, with its own
     * expiration. See {@link ApiTokenLifetimePolicy}.
     * <p>
     * The exception is the strict mode of the maximum lifetime, see {@link ApiTokenLifetimePolicy#isStrict}. While it is in
     * effect nobody is exempt, the expiration must respect the maximum, and a secret that this user holds already is
     * refused, as the store does, see {@link ApiTokenStore#addFixedNewToken(String, String, LocalDate)}. Provisioning scripts
     * that give no expiration, or register the same secret at every start, fail then.
     *
     * @throws ApiTokenPolicyViolationException only while the strict mode is in effect. Nothing is stored or saved then.
     * @throws IllegalArgumentException when the token value is malformed
     */
    @Restricted(Beta.class)
    public @NonNull String addFixedNewToken(@NonNull String name, @NonNull String tokenPlainValue, @Nullable LocalDate expirationDate) throws IOException {
        String tokenUuid = this.tokenStore.addFixedNewToken(name, tokenPlainValue, expirationDate);
        user.save();
        return tokenUuid;
    }

    /**
     * Essentially meant for scripting.
     * <p>
     * The token never expires, so this fails while the strict mode of the maximum lifetime of API tokens is in effect: see
     * {@link #generateNewToken(String, LocalDate)}.
     *
     * @throws ApiTokenPolicyViolationException only while the strict mode is in effect
     */
    @Restricted(Beta.class)
    public @NonNull TokenUuidAndPlainValue generateNewToken(@NonNull String name) throws IOException {
        return generateNewToken(name, null);
    }

    /**
     * Essentially meant for scripting.
     * <p>
     * This does not apply the maximum lifetime of API tokens, and any expiration is accepted, unless the strict mode is in
     * effect, see {@link ApiTokenLifetimePolicy#isStrict}: then nobody is exempt and the token must expire, no later than today
     * plus the maximum. That includes the tokens that never expire, and so the {@code JenkinsRule.createApiToken} of the
     * tests and provisioning scripts that give no expiration.
     *
     * @throws ApiTokenPolicyViolationException only while the strict mode is in effect. Nothing is stored or saved then.
     */
    @Restricted(Beta.class)
    public @NonNull TokenUuidAndPlainValue generateNewToken(@NonNull String name, @Nullable LocalDate expirationDate) throws IOException {
        TokenUuidAndPlainValue tokenUuidAndPlainValue = tokenStore.generateNewToken(name, expirationDate);
        user.save();
        return tokenUuidAndPlainValue;
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public void revokeAllTokens() throws IOException {
        tokenStats.removeAll();
        tokenStore.revokeAllTokens();
        // otherwise the legacy token is created again from this value the next time the user is loaded, see setUser
        apiToken = null;
        user.save();
    }

    // essentially meant for scripting
    @Restricted(Beta.class)
    public void revokeAllTokensExceptOne(@NonNull String tokenUuid) throws IOException {
        tokenStats.removeAllExcept(tokenUuid);
        tokenStore.revokeAllTokensExcept(tokenUuid);
        if (tokenStore.getLegacyToken() == null) {
            // the legacy token was not the one kept: same reason as in revokeAllTokens
            apiToken = null;
        }
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

    /**
     * Revokes the token with the UUID {@code tokenUuid}, whether it has expired or not, and every other token of this user that
     * can still be used on {@code today} and has the same secret, in one operation of the store, see
     * {@link ApiTokenStore#revokeLiveEntriesSharingSecretWith}. A token whose secret is not shared is revoked alone, so
     * the caller does not decide beforehand whether the secret is shared. Tokens that have expired and have the same secret
     * are kept, as they cannot authenticate.
     * <p>
     * For every token that was removed it does what {@link #revokeToken} does for one: its usage statistics are removed, and
     * when one of them is the legacy token the legacy secret is dropped, or the legacy token would be created again from it
     * the next time the user is loaded (see {@link #setUser}). The user is saved once, and only if a token was removed.
     * <p>
     * Like {@link #revokeToken}, it does not check any permission: the caller, the page of the administrative monitor that
     * lists the tokens, is the one that does, as it only serves administrators.
     *
     * @param today the day that decides which tokens have expired, which the caller chooses once for what it does
     * @return the UUIDs of the tokens that were revoked, none if this user has no token with that UUID
     */
    @Restricted(NoExternalUse.class)
    public @NonNull List<String> revokeTokens(@NonNull String tokenUuid, @NonNull LocalDate today) throws IOException {
        List<ApiTokenStore.HashedToken> revoked = tokenStore.revokeLiveEntriesSharingSecretWith(tokenUuid, today);
        if (revoked.isEmpty()) {
            return List.of();
        }

        List<String> revokedUuids = new ArrayList<>(revoked.size());
        for (ApiTokenStore.HashedToken token : revoked) {
            if (token.isLegacy()) {
                // same reason as in revokeToken
                apiToken = null;
            }
            tokenStats.removeId(token.getUuid());
            revokedUuids.add(token.getUuid());
        }
        user.save();
        return revokedUuids;
    }

    /**
     * Makes the expiration of the token with the UUID {@code tokenUuid} earlier, and saves the user if it did. It never makes it
     * later, and it does not change the legacy token, see {@link ApiTokenStore#shortenExpiration}.
     * <p>
     * Like {@link #revokeToken}, it does not check any permission: the caller, the page of the administrative monitor that
     * lists the tokens, is the one that does, as it only serves administrators.
     *
     * @return {@code true} iff the expiration was changed, which is when the user was saved
     */
    @Restricted(NoExternalUse.class)
    public boolean shortenExpiration(@NonNull String tokenUuid, @NonNull LocalDate newExpiry) throws IOException {
        boolean shortened = tokenStore.shortenExpiration(tokenUuid, newExpiry);
        if (shortened) {
            user.save();
        }
        return shortened;
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
         * The expiration that the dialog which adds a token offers, and what it says about the maximum lifetime of API tokens,
         * see {@link ApiTokenLifetimePolicy}. The user of the page, {@code propertyOwner}, is the one that is judged, not the
         * user who looks at it. Everything is computed here, from one date, and the view only displays it.
         */
        @Restricted(NoExternalUse.class) // for Jelly view
        public ExpirationOptions getExpirationOptions(User propertyOwner) {
            LocalDate today = LocalDate.now();
            ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
            boolean maximumApplies = policy.appliesTo(propertyOwner, today);
            return ExpirationOptions.of(today, maximumApplies, ApiTokenPropertyConfiguration.get().getMaxTokenLifetimeDays(), policy.maxExpiration(today), policy.getPendingEnforceFrom(today), policy.isStrictScheduled(today));
        }

        /**
         * What the dialog that adds a token offers as expiration for the tokens of one owner, besides a date of the user's choice.
         * <p>
         * When the maximum applies to the owner, that is when the policy is enforcing and the owner is not exempt, only the usual
         * choices that are not longer than the maximum are offered, and the maximum itself if it is not one of them. A token
         * without expiration is not offered. Otherwise the usual choices are offered, and a token without expiration.
         *
         * @param days the numbers of days offered, in increasing order
         * @param explicitMaximum the number of {@code days} that is offered because it is the maximum and not one of the usual
         *                        choices, or 0 if there is none
         * @param defaultDays the number of {@code days} that is selected when the dialog opens: the largest one that is not longer
         *                    than 30 days
         * @param maximumApplies whether the maximum applies to the owner, so that a token cannot be created without expiration
         * @param pendingEnforceFrom the date from which the maximum will be enforced, when it is announced but not enforced yet,
         *                           otherwise {@code null}
         * @param strictScheduled whether the strict mode is switched on and starts on {@code pendingEnforceFrom}: nobody will be
         *                        exempt from then on, so the announcement does not mention exemptions
         * @param maximumLifetimeDays the maximum lifetime of a token in days, 0 if there is none
         * @param minDate the earliest date that the date picker of the custom choice accepts: the day of the server
         * @param maxDate the latest date that the date picker accepts: the latest expiration the maximum allows if it applies,
         *                otherwise a year from {@code minDate}, in both cases no later than the last date that a date input can show
         * @param presetDate the date that the date picker shows when the custom choice is selected: 30 days from {@code minDate},
         *                   or {@code maxDate} if that is sooner
         */
        @Restricted(NoExternalUse.class)
        public record ExpirationOptions(List<Integer> days, int explicitMaximum, int defaultDays, boolean maximumApplies,
                                        @CheckForNull LocalDate pendingEnforceFrom, boolean strictScheduled, int maximumLifetimeDays,
                                        LocalDate minDate, LocalDate maxDate, LocalDate presetDate) {

            private static final int[] USUAL_DAYS = {7, 30, 60, 90};

            private static final int USUAL_DEFAULT_DAYS = 30;

            /** How far the date picker reaches when there is no maximum to limit it. */
            private static final int UNLIMITED_PICKER_DAYS = 365;

            /** The last date that is valid in the {@code max} of an {@code <input type="date">}: its years have four digits. */
            private static final LocalDate LAST_PICKER_DATE = LocalDate.of(9999, 12, 31);

            public ExpirationOptions {
                days = List.copyOf(days);
            }

            /**
             * @param today the date of the server, from which all the dates are computed
             * @param maximumExpiration {@code today} plus the maximum lifetime, used when the maximum applies
             */
            static ExpirationOptions of(LocalDate today, boolean maximumApplies, int maximumLifetimeDays, LocalDate maximumExpiration, @CheckForNull LocalDate pendingEnforceFrom, boolean strictScheduled) {
                List<Integer> days = new ArrayList<>();
                for (int usual : USUAL_DAYS) {
                    if (!maximumApplies || usual <= maximumLifetimeDays) {
                        days.add(usual);
                    }
                }
                int explicitMaximum = 0;
                if (maximumApplies && !days.contains(maximumLifetimeDays)) {
                    days.add(maximumLifetimeDays);
                    explicitMaximum = maximumLifetimeDays;
                }
                // the numbers are in increasing order, and there is always at least one
                int defaultDays = days.get(0);
                for (int offered : days) {
                    if (offered <= USUAL_DEFAULT_DAYS) {
                        defaultDays = offered;
                    }
                }
                LocalDate maxDate = maximumApplies ? maximumExpiration : today.plusDays(UNLIMITED_PICKER_DAYS);
                if (maxDate.isAfter(LAST_PICKER_DATE)) {
                    maxDate = LAST_PICKER_DATE;
                }
                LocalDate presetDate = today.plusDays(USUAL_DEFAULT_DAYS);
                if (presetDate.isAfter(maxDate)) {
                    presetDate = maxDate;
                }
                return new ExpirationOptions(days, explicitMaximum, defaultDays, maximumApplies, pendingEnforceFrom, strictScheduled, maximumLifetimeDays, today, maxDate, presetDate);
            }
        }

        /**
         * Regenerates the legacy token of the user. A legacy token never expires, so once the maximum lifetime of API tokens
         * is enforced (see {@link ApiTokenLifetimePolicy}) this endpoint is rejected, with an HTML message, when the owner
         * of the token is not exempt from the maximum. The owner is the user of the page, not the user who sends the request.
         * While the strict mode is in effect nobody is exempt, so it is rejected for every owner. The Java method
         * {@link ApiTokenProperty#changeApiToken()} checks the maximum only in the strict mode.
         *
         * @deprecated use {@link #doGenerateNewToken(User, String, String, String)} instead
         */
        @Deprecated
        @RequirePOST
        public HttpResponse doChangeToken(@AncestorInPath User u, StaplerResponse rsp) throws IOException {
            // you are the user or you have ADMINISTER permission
            u.checkPermission(Jenkins.ADMINISTER);

            LOGGER.log(Level.FINE, "Deprecated action /changeToken used, consider using /generateNewToken instead");

            LocalDate today = LocalDate.now();
            ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
            if (policy.appliesTo(u, today)) {
                // the regenerated legacy token would have no expiry: it would bypass the maximum lifetime
                return HttpResponses.html(Messages.ApiTokenProperty_ChangeToken_MaximumLifetimeEnforced());
            }

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
                try {
                    p.changeApiToken();
                } catch (ApiTokenPolicyViolationException e) {
                    // the strict mode was switched on after the check above, nothing was changed: same answer as that check
                    return HttpResponses.html(Messages.ApiTokenProperty_ChangeToken_MaximumLifetimeEnforced());
                }
            }

            rsp.setHeader("X-Jenkins-ValidateButton-Callback", "{\"callback\":\"changeTokenCallback\",\"arguments\":[\"" + p.getApiTokenInsecure() + "\"]}");
            return HttpResponses.html(p.hasPermissionToSeeToken()
                    ? Messages.ApiTokenProperty_ChangeToken_Success()
                    : Messages.ApiTokenProperty_ChangeToken_SuccessHidden());
        }

        /**
         * Creates a new token for the user {@code u}, the owner of the token, and answers with its value and its expiration.
         * <p>
         * When a maximum lifetime of API tokens is configured and enforced (see {@link ApiTokenLifetimePolicy}), this HTTP endpoint
         * applies it, unless the owner is exempt: a token that never expires or that expires later than today plus the maximum
         * is rejected, and an empty {@code expirationDuration} creates a token that expires on the last day that is allowed.
         * The owner is judged, not the user who sends the request. Clients should read the expiration of the token that was
         * created from {@code expirationDateIso} in the response.
         * The Java API ({@link ApiTokenProperty#generateNewToken(String, LocalDate)}) and {@link ApiTokenStore} enforce the maximum
         * only in the strict mode, in which nobody is exempt, and that mode applies to this endpoint as well.
         * <p>
         * Rejections are answered with {@link HttpResponses#errorJSON(String)} (HTTP status 200 and {@code "status":"error"}).
         * That includes a token that the store refuses, which the strict mode can do when it is switched on, or the enforcement
         * date is reached, after the check of this method; nothing is stored then.
         *
         * @param newTokenName the name of the token, a default name is used if it is blank
         * @param tokenExpiration the expiration date, as {@code yyyy-MM-dd}, used only when {@code expirationDuration} is {@code custom}
         * @param expirationDuration a number of days, {@code custom}, {@code never} or empty
         */
        @RequirePOST
        public HttpResponse doGenerateNewToken(@AncestorInPath User u, @QueryParameter String newTokenName,
                                               @QueryParameter String tokenExpiration, @QueryParameter String expirationDuration) throws IOException {
            if (!hasCurrentUserRightToGenerateNewToken(u)) {
                return HttpResponses.forbidden();
            }

            final String tokenName;
            if (newTokenName == null || newTokenName.isBlank()) {
                tokenName = Messages.Token_Created_on(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
            } else {
                tokenName = newTokenName;
            }

            // Everything that does not need the store is validated before the property is built or added:
            // User#addProperty saves the user, and a rejected request must not change anything.
            LocalDate today = LocalDate.now();
            RequestedExpiration requestedExpiration = resolveExpiration(u, tokenExpiration, expirationDuration, today);
            if (requestedExpiration.rejection() != null) {
                return requestedExpiration.rejection();
            }
            LocalDate expirationDate = requestedExpiration.date();

            ApiTokenProperty existing = u.getProperty(ApiTokenProperty.class);
            ApiTokenProperty p = existing;
            if (p == null) {
                p = forceNewInstance(u, false);
                // creates the token store and the statistics, the store call below needs them;
                // the property is only added to the user once the token has been stored
                p.setUser(u);
            }

            TokenUuidAndPlainValue tokenUuidAndPlainValue;
            try {
                tokenUuidAndPlainValue = p.tokenStore.generateNewToken(tokenName, expirationDate);
            } catch (ApiTokenPolicyViolationException e) {
                // The store reads the configuration and the clock again: the strict mode can have been switched on, or the
                // enforcement date reached, since the check above. Nothing was stored, nor saved.
                return rejection(e);
            }
            if (existing == null) {
                u.addProperty(p);
            } else {
                u.save();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("tokenUuid", tokenUuidAndPlainValue.tokenUuid);
            data.put("tokenName", tokenName);
            data.put("tokenValue", tokenUuidAndPlainValue.plainValue);
            putExpirationDates(data, expirationDate);
            data.put("aboutToExpire", tokenUuidAndPlainValue.aboutToExpire);
            return HttpResponses.okJSON(data);
        }

        /**
         * What the caller asked for as expiration: nothing, an explicit {@code never}, or a date.
         * Without a maximum lifetime an empty duration and {@code never} both mean "no expiry", but they are different requests:
         * under an enforced maximum the first one gets the latest allowed expiration and the second one is rejected.
         */
        private enum ExpirationChoice {
            /** The duration is missing or empty. */
            UNSPECIFIED,
            /** The duration is {@code never}. */
            NEVER,
            /** The duration is a number of days or {@code custom} with a date. */
            DATE
        }

        /**
         * The outcome of {@link #parseExpiration} and {@link #resolveExpiration}: either the expiration of the token to create,
         * or the rejection to answer with.
         *
         * @param choice what was asked for, {@code null} when the request is rejected
         * @param date the expiration date of the token to create, {@code null} if it does not expire. It is the date that was asked
         *             for, or the latest date that the maximum lifetime allows when none was asked for
         * @param rejection the {@code errorJSON} response when the request is invalid, otherwise {@code null}
         */
        private record RequestedExpiration(@CheckForNull ExpirationChoice choice, @CheckForNull LocalDate date, @CheckForNull HttpResponse rejection) {
            static RequestedExpiration of(ExpirationChoice choice, @CheckForNull LocalDate date) {
                return new RequestedExpiration(choice, date, null);
            }

            static RequestedExpiration rejected(String message) {
                return new RequestedExpiration(null, null, HttpResponses.errorJSON(message));
            }
        }

        /**
         * Parses and validates the expiration of a token creation request, for {@link #doGenerateNewToken} and {@link #doAddFixedToken}.
         * Nothing here needs the token store, so it runs before anything is changed.
         * The messages of the rejections are static: they never contain the submitted values.
         *
         * @param today the date of the request, computed once by the caller and used for relative durations and for the past-date check
         */
        private static RequestedExpiration parseExpiration(@CheckForNull String tokenExpiration, @CheckForNull String expirationDuration, LocalDate today) {
            String duration = expirationDuration == null ? "" : expirationDuration.trim();

            RequestedExpiration requested;
            try {
                requested = switch (duration) {
                    case "" -> RequestedExpiration.of(ExpirationChoice.UNSPECIFIED, null);
                    case "never" -> RequestedExpiration.of(ExpirationChoice.NEVER, null);
                    case "custom" -> {
                        if (tokenExpiration == null || tokenExpiration.isBlank()) {
                            yield RequestedExpiration.rejected(Messages.ApiTokenProperty_expirationDateMissing());
                        }
                        yield RequestedExpiration.of(ExpirationChoice.DATE, LocalDate.parse(tokenExpiration));
                    }
                    default -> RequestedExpiration.of(ExpirationChoice.DATE, today.plusDays(Integer.parseInt(duration)));
                };
            } catch (NumberFormatException e) {
                return RequestedExpiration.rejected(Messages.ApiTokenProperty_expirationDurationInvalid());
            } catch (DateTimeParseException e) {
                return RequestedExpiration.rejected(Messages.ApiTokenProperty_expirationDateInvalid());
            }

            LocalDate date = requested.date();
            if (date != null && date.isBefore(today)) {
                // also covers a negative number of days
                return RequestedExpiration.rejected(Messages.ApiTokenProperty_expirationInPast());
            }
            return requested;
        }

        /**
         * The shared validation step of {@link #doGenerateNewToken} and {@link #doAddFixedToken}: parses the expiration of the
         * request with {@link #parseExpiration} and applies the maximum lifetime of API tokens to it. Nothing here needs the
         * token store, so it runs before anything is changed.
         * <p>
         * The maximum applies when the policy is enforcing at {@code today} and {@code owner} is not exempt, see
         * {@link ApiTokenLifetimePolicy}. Then:
         * <ul>
         * <li>an empty duration gives the latest expiration that is allowed, today plus the maximum, as the endpoints are used
         * by automation that sends only the name of the token: it keeps working and the response says when the token expires;
         * <li>{@code never}, and a date later than today plus the maximum, are rejected. That last day is accepted, a token is
         * valid through its expiration date.
         * </ul>
         * Otherwise (no maximum, a maximum that is not enforced yet, or an exempt owner) the request is left as {@link #parseExpiration}
         * returned it. In the strict mode nobody is exempt, so the maximum applies to every owner. The Java API and
         * {@link ApiTokenStore} enforce the maximum in that mode only, which is why the store can still refuse a request that
         * this method accepted.
         * <p>
         * The messages of the rejections are static: they contain no submitted value, only the configured maximum.
         *
         * @param owner the user the token is created for, which is not necessarily the user who sends the request
         * @param today the date of the request, computed once by the caller
         */
        private static RequestedExpiration resolveExpiration(User owner, @CheckForNull String tokenExpiration, @CheckForNull String expirationDuration, LocalDate today) {
            RequestedExpiration requested = parseExpiration(tokenExpiration, expirationDuration, today);
            if (requested.rejection() != null) {
                return requested;
            }

            // a new instance per request: the configuration is read when the policy is asked, nothing is cached
            ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
            if (!policy.appliesTo(owner, today)) {
                return requested;
            }

            LocalDate expiration = requested.choice() == ExpirationChoice.UNSPECIFIED ? policy.maxExpiration(today) : requested.date();
            ApiTokenLifetimePolicy.Violation violation = policy.validate(owner, expiration, today);
            if (violation == null) {
                return RequestedExpiration.of(requested.choice(), expiration);
            }
            return switch (violation) {
                case NEVER_NOT_ALLOWED -> RequestedExpiration.rejected(Messages.ApiTokenProperty_expirationNeverNotAllowed());
                case OVER_MAXIMUM -> RequestedExpiration.rejected(
                        Messages.ApiTokenProperty_expirationOverMaximum(ApiTokenPropertyConfiguration.get().getMaxTokenLifetimeDays()));
            };
        }

        /**
         * The answer to a token creation that the store refused. The message of the exception is for logs and scripts: the
         * response gets a localized one, chosen by the reason, which contains no submitted value, only the configured maximum.
         */
        private static HttpResponse rejection(ApiTokenPolicyViolationException e) {
            return HttpResponses.errorJSON(switch (e.getReason()) {
                case NEVER_EXPIRES -> Messages.ApiTokenProperty_expirationNeverNotAllowed();
                case OVER_MAXIMUM -> Messages.ApiTokenProperty_expirationOverMaximum(ApiTokenPropertyConfiguration.get().getMaxTokenLifetimeDays());
                case SECRET_ALREADY_REGISTERED -> Messages.ApiTokenProperty_SecretAlreadyRegistered();
            });
        }

        /**
         * Adds the expiration to the response of a token creation: {@code expirationDate} is localized, as shown to the user,
         * {@code expirationDateIso} is the ISO-8601 date for clients. Both are {@code never} when the token does not expire.
         */
        private static void putExpirationDates(Map<String, Object> data, @CheckForNull LocalDate expirationDate) {
            if (expirationDate == null) {
                data.put("expirationDate", "never");
                data.put("expirationDateIso", "never");
            } else {
                data.put("expirationDate", expirationDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                        .withLocale(Functions.getCurrentLocale())));
                data.put("expirationDateIso", expirationDate.toString());
            }
        }

        /**
         * This method is dangerous and should not be used without caution.
         * The token passed here could have been tracked by different network system during its trip.
         * It is recommended to revoke this token after the generation of a new one.
         * <p>
         * The maximum lifetime of API tokens is applied as in {@link #doGenerateNewToken}: when it is configured and enforced
         * (see {@link ApiTokenLifetimePolicy}) this HTTP endpoint rejects a token that never expires, or that expires later than
         * today plus the maximum, unless the owner {@code u} is exempt, and an empty {@code expirationDuration} creates a token that
         * expires on the last day that is allowed. The Java API ({@link ApiTokenProperty#addFixedNewToken(String, String, LocalDate)})
         * and {@link ApiTokenStore} enforce the maximum only in the strict mode, in which nobody is exempt, and that mode applies
         * to this endpoint as well.
         * <p>
         * In the same case, when the maximum applies to the owner, a secret that the store of the owner holds already is rejected
         * too, whether the entry that holds it has expired or not, and whichever the letter case of the value is. Registering the
         * same secret again would give it a new expiration each time, so the maximum would limit the entries but not the secret:
         * to keep working, the owner generates a new secret. The check and the insertion are one operation of the store, see
         * {@link ApiTokenStore#addFixedNewTokenRejectingKnownSecret}. The rejection is answered like the other ones, and it is
         * the last check: a request that is over the maximum is rejected for that. The Java API checks it only in the strict mode,
         * in which it applies to every owner, and {@link #doGenerateNewToken} never does, as its secret is random.
         */
        @RequirePOST
        @Restricted(NoExternalUse.class)
        public HttpResponse doAddFixedToken(@AncestorInPath User u,
                                            @QueryParameter String newTokenName,
                                            @QueryParameter String newTokenPlainValue,
                                            @QueryParameter String tokenExpiration,
                                            @QueryParameter String expirationDuration) throws IOException {
            if (!hasCurrentUserRightToGenerateNewToken(u)) {
                return HttpResponses.forbidden();
            }

            final String tokenName;
            if (newTokenName == null || newTokenName.isBlank()) {
                tokenName = String.format("Token created on %s", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
            } else {
                tokenName = newTokenName;
            }

            if (newTokenPlainValue == null || newTokenPlainValue.isBlank()) {
                return HttpResponses.errorJSON(Messages.ApiTokenProperty_tokenValueMissing());
            }

            // Everything that does not need the store is validated before the property is built or added.
            LocalDate today = LocalDate.now();
            RequestedExpiration requestedExpiration = resolveExpiration(u, tokenExpiration, expirationDuration, today);
            if (requestedExpiration.rejection() != null) {
                return requestedExpiration.rejection();
            }
            LocalDate expirationDate = requestedExpiration.date();

            ApiTokenProperty existing = u.getProperty(ApiTokenProperty.class);
            ApiTokenProperty p = existing;
            if (p == null) {
                p = forceNewInstance(u, false);
                // creates the token store and the statistics, the store call below needs them;
                // the property is only added to the user once the token has been stored
                p.setUser(u);
            }

            // The same secret registered again would get a new expiration each time: while the maximum applies to the owner,
            // the store refuses a secret it holds already. Otherwise nothing changes, duplicates are stored as they always were.
            boolean rejectKnownSecret = new ApiTokenLifetimePolicy().appliesTo(u, today);

            String tokenUuid;
            try {
                tokenUuid = rejectKnownSecret
                        ? p.tokenStore.addFixedNewTokenRejectingKnownSecret(tokenName, newTokenPlainValue, expirationDate)
                        : p.tokenStore.addFixedNewToken(tokenName, newTokenPlainValue, expirationDate);
            } catch (ApiTokenPolicyViolationException e) {
                // This catch must stay before the one of IllegalArgumentException, which the exception is a subclass of.
                // For the lifetime: the store reads the configuration and the clock again, so the strict mode can have been
                // switched on, or the enforcement date reached, since the check above
                return rejection(e);
            } catch (IllegalArgumentException e) {
                // wrong length, unknown version or not hexadecimal. The message of the store contains the submitted value
                return HttpResponses.errorJSON(Messages.ApiTokenProperty_tokenValueInvalid());
            }
            if (existing == null) {
                u.addProperty(p);
            } else {
                u.save();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("tokenUuid", tokenUuid);
            data.put("tokenName", tokenName);
            putExpirationDates(data, expirationDate);
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

    @Restricted(NoExternalUse.class)
    public Badge getBadge() {
        long expiringTokenCount = getTokenList().stream().filter(t -> t.aboutToExpire && !t.expired).count();
        long expiredTokenCount = getTokenList().stream().filter(t -> t.expired).count();
        StringBuilder tooltip = new StringBuilder();
        if (expiringTokenCount > 0) {
            tooltip.append(Messages.ApiTokenProperty_aboutToExpireTokens(expiringTokenCount));
        }
        if (expiredTokenCount > 0) {
            if (expiringTokenCount > 0) {
                tooltip.append("\n");
            }
            tooltip.append(Messages.ApiTokenProperty_expiredTokens(expiredTokenCount));
        }
        if (expiredTokenCount + expiringTokenCount > 0) {
            return new Badge(Long.toString(expiringTokenCount + expiredTokenCount), tooltip.toString(), Badge.Severity.WARNING);
        } else {
            return null;
        }
    }
}
