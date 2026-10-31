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

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.model.User;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenLifetimePolicy.Phase;
import jenkins.security.apitoken.ApiTokenStore.HashedToken;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Reports the API tokens that do not comply with the maximum lifetime that the administrator configured, see
 * {@link ApiTokenLifetimePolicy}, and the secrets that several tokens of one user hold. The policy only refuses tokens at the
 * moment they are created, and only through the HTTP endpoints: this is where the administrator finds the ones that it did
 * not stop, such as the tokens that existed before the maximum was set, the legacy tokens, which never expire, and the
 * tokens that scripts create.
 * <p>
 * A token is reported in every phase of the policy, the warning phase included, as long as a maximum is configured and
 * its owner is not exempt. A secret that is held by two or more tokens of a user that have not expired is reported once,
 * and every one of those tokens is listed. A token that has expired cannot authenticate, so it is not reported, and
 * neither is a secret that only one token that has not expired holds.
 * <p>
 * Like {@link LegacyApiTokenAdministrativeMonitor}, from which it takes the pattern, it is {@linkplain #isSecurity() a
 * security monitor} that shows a banner and links to a page, but it is not a subclass of it.
 * <p>
 * Access control: this class keeps the default of {@link AdministrativeMonitor}, which is a {@code StaplerProxy} whose
 * {@link #getTarget()} checks {@link #checkRequiredPermission()}, that is {@code Jenkins.ADMINISTER}, before Stapler reaches
 * the page or any web method of the monitor. That is the only guard, and it must stay so: the methods of
 * {@link ApiTokenProperty} that the actions of the page use, such as {@code revokeTokens} and {@code shortenExpiration}, do
 * not check any permission of their own, because they trust the page that calls them to be reachable by administrators only.
 * <p>
 * The page offers two actions, {@link #doRevoke} and {@link #doShorten}. Each one is a POST that names a token by the ID of its
 * owner and its UUID, and each one decides from what the token is when the request arrives, not from what the page showed
 * when it was loaded: the group of tokens that share a secret and whether a token complies are computed again, and an action
 * that no longer applies is refused with a status of 4xx and a message that does not repeat the request. Every action that is
 * done is logged, with the administrator, the owner and the UUIDs, but never with the name of a token, which users choose.
 */
@Extension
@Symbol("apiTokenLifetime")
@Restricted(NoExternalUse.class)
public class ApiTokenLifetimeAdministrativeMonitor extends AdministrativeMonitor {

    private static final Logger LOGGER = Logger.getLogger(ApiTokenLifetimeAdministrativeMonitor.class.getName());

    /**
     * The most that is read of the body of an action: {@code {"userId": ..., "uuid": ...}} takes well under a kilobyte.
     */
    private static final int MAX_BODY_BYTES = 8 * 1024;

    public ApiTokenLifetimeAdministrativeMonitor() {
        super("apiTokenLifetime");
    }

    @Override
    public String getDisplayName() {
        return Messages.ApiTokenLifetimeAdministrativeMonitor_displayName();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    /**
     * Whether there is something to report: a maximum is configured and, either the enforcement date does not parse, which
     * is known without looking at any token, or some user who is not exempt has a token that does not comply, or two tokens
     * that have not expired and hold the same secret. It stops at the first one it finds, and it keeps no result: the
     * tokens are scanned each time, as {@link LegacyApiTokenAdministrativeMonitor#isActivated()} does.
     */
    @Override
    public boolean isActivated() {
        return isActivated(LocalDate.now());
    }

    /**
     * @param today the day that decides which tokens have expired and how far the maximum reaches, the same one for
     *              everything that is checked
     */
    boolean isActivated(@NonNull LocalDate today) {
        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        Phase phase = policy.getPhase(today);
        if (phase == Phase.INACTIVE) {
            return false;
        }
        if (phase == Phase.ENFORCING_INVALID_DATE) {
            // the date fails closed, which is a finding by itself, so no token has to be looked at
            return true;
        }

        // User#getAll only reads: it does not create the users that are not known
        for (User user : User.getAll()) {
            ApiTokenStore store = storeOf(user);
            if (store == null) {
                continue;
            }
            for (HashedToken token : store.getTokenListSnapshot()) {
                if (!policy.isCompliant(user, token, today)) {
                    return true;
                }
            }
            if (store.hasLiveSharedSecret(today) && policy.isMonitored(user, today)) {
                return true;
            }
        }
        return false;
    }

    public HttpResponse doIndex() throws IOException {
        return new HttpRedirect("manage");
    }

    /**
     * Revokes a token, and with it every token of the same user that has not expired and holds the same secret, as one
     * operation, see {@link ApiTokenProperty#revokeTokens}. Whatever the page showed, the group is worked out here from the UUID
     * alone, and never taken from the request. A token whose secret is not shared is revoked alone, a token that has expired
     * is revoked as well, and the tokens that have expired and share its secret are kept.
     * <p>
     * The body of the request is JSON, {@code {"userId": ..., "uuid": ...}}, with the ID of the user and the UUID of the token.
     * Answers 400 if it does not say which token, and 404 if there is no such user, or the user has no token with that UUID,
     * which is what a second request for the same token gets.
     */
    @RequirePOST
    public HttpResponse doRevoke(StaplerRequest2 req) throws IOException {
        LocalDate today = LocalDate.now();
        TokenId id = readTokenId(req);
        String uuid = uuidOf(id);
        User owner = ownerOf(id);
        ApiTokenProperty property = propertyOf(owner);

        List<String> revoked = property.revokeTokens(uuid, today);
        if (revoked.isEmpty()) {
            throw notFound();
        }

        LOGGER.log(Level.INFO, "API token revoked in the compliance panel: administrator {0}, owner {1}, revoked tokens {2}",
                new Object[] {Jenkins.getAuthentication2().getName(), owner.getId(), String.join(",", revoked)});
        return HttpResponses.okJSON();
    }

    /**
     * Makes the expiration of a token that does not comply with the maximum lifetime today plus the maximum. It checks again,
     * when the request arrives, that the token still needs it: the maximum is configured and its owner is not exempt, the token
     * exists, it is not the legacy token, which has no expiration to change, and it neither expires within the maximum nor has
     * expired. Otherwise nothing is changed and the answer is 400 for the legacy token and 409 for a token that does not need
     * it, so that a page that is out of date cannot extend a token or shorten one that complies. It never makes a token expire
     * later, which {@link ApiTokenProperty#shortenExpiration} guarantees even if the token changes meanwhile.
     * <p>
     * It changes the token that was asked for only: the other tokens that share its secret are listed on their own if they do
     * not comply. The body of the request is the same as for {@link #doRevoke}. Answers 400 if it does not say which token, and
     * 404 if there is no such user or token.
     */
    @RequirePOST
    public HttpResponse doShorten(StaplerRequest2 req) throws IOException {
        LocalDate today = LocalDate.now();
        TokenId id = readTokenId(req);
        String uuid = uuidOf(id);
        User owner = ownerOf(id);
        ApiTokenProperty property = propertyOf(owner);

        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        // the maximum may have been removed, or the owner exempted, since the page was loaded
        if (!policy.isMonitored(owner, today)) {
            throw shortenNotNeeded();
        }
        HashedToken token = null;
        for (HashedToken candidate : property.getTokenStore().getTokenListSnapshot()) {
            if (candidate.getUuid().equals(uuid)) {
                token = candidate;
                break;
            }
        }
        if (token == null) {
            throw notFound();
        }
        if (token.isLegacy()) {
            throw shortenLegacy();
        }
        // a token that has expired complies as well: it cannot be used
        if (policy.isCompliant(owner, token, today)) {
            throw shortenNotNeeded();
        }

        LocalDate previous = token.getExpirationDate();
        LocalDate shortened = policy.maxExpiration(today);
        if (!property.shortenExpiration(token.getUuid(), shortened)) {
            // the token changed since it was read here, and it is no longer later than the maximum
            throw shortenNotNeeded();
        }

        LOGGER.log(Level.INFO, "API token shortened in the compliance panel: administrator {0}, owner {1}, token {2}, expiration {3} to {4}",
                new Object[] {Jenkins.getAuthentication2().getName(), owner.getId(), token.getUuid(), previous == null ? "never" : previous.toString(), shortened.toString()});
        return HttpResponses.okJSON();
    }

    /**
     * What an action of the page sends, as JSON: the token to act on, by the ID of its owner and its UUID. They are what the
     * page rendered, but they are the input of anyone who can send a request to the page, and are only looked up.
     * <p>
     * The body is read here and not bound by Stapler with {@code @JsonBody}, as {@link LegacyApiTokenAdministrativeMonitor}
     * does: that one fails before the web method runs, and so before {@code @RequirePOST} can answer, with a status of 500
     * and an error page that has the stack trace for a GET and for a body that is not JSON, and it reads the ID {@code "null"}
     * as no ID. Here a request that is not JSON of the expected shape is a 400 with a static message, and so is a body that
     * is larger than {@link #MAX_BODY_BYTES}, which is refused before it is parsed. A value that is not a string is taken
     * as missing.
     */
    private static @NonNull TokenId readTokenId(@NonNull StaplerRequest2 req) throws IOException {
        String contentType = req.getContentType();
        if (contentType == null || !contentType.startsWith("application/json")) {
            throw invalid();
        }
        // The two fields are tiny: one byte more than the limit is enough to know that the body is too large, without
        // reading the rest of it.
        byte[] body = req.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw invalid();
        }
        try {
            // JSON is UTF-8, whatever the container assumes when the content type has no charset. The fields are read inside the
            // try as well: the text null is parsed as a JSON null object, whose every read throws a JSONException.
            JSONObject json = JSONObject.fromObject(new String(body, StandardCharsets.UTF_8));
            return new TokenId(stringOf(json, "userId"), stringOf(json, "uuid"));
        } catch (JSONException e) {
            throw invalid();
        }
    }

    private static @CheckForNull String stringOf(@NonNull JSONObject json, @NonNull String key) {
        return json.opt(key) instanceof String value ? value : null;
    }

    private static @NonNull String uuidOf(@NonNull TokenId id) {
        String uuid = id.uuid();
        if (uuid == null || uuid.isBlank()) {
            throw invalid();
        }
        return uuid;
    }

    /**
     * The owner of the token, who is looked up and never created.
     */
    private static @NonNull User ownerOf(@NonNull TokenId id) {
        String userId = id.userId();
        if (userId == null || userId.isBlank()) {
            throw invalid();
        }
        User owner = User.getById(userId, false);
        if (owner == null) {
            throw notFound();
        }
        return owner;
    }

    /**
     * The property that holds the tokens of the user. The page skips the users that have none, see {@link #scan}, so they are
     * answered as a token that does not exist.
     */
    private static @NonNull ApiTokenProperty propertyOf(@NonNull User owner) {
        ApiTokenProperty property = owner.getProperty(ApiTokenProperty.class);
        if (property == null || property.getTokenStore() == null || property.getTokenStats() == null) {
            throw notFound();
        }
        return property;
    }

    private static HttpResponseException invalid() {
        return HttpResponses.errorWithoutStack(SC_BAD_REQUEST, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_Invalid());
    }

    private static HttpResponseException notFound() {
        return HttpResponses.errorWithoutStack(SC_NOT_FOUND, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_NotFound());
    }

    private static HttpResponseException shortenLegacy() {
        return HttpResponses.errorWithoutStack(SC_BAD_REQUEST, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenLegacy());
    }

    private static HttpResponseException shortenNotNeeded() {
        return HttpResponses.errorWithoutStack(SC_CONFLICT, Messages.ApiTokenLifetimeAdministrativeMonitor_Action_ShortenNotNeeded());
    }

    /**
     * The token that an action is about, as the request said, which may be anything: both are looked up and none is trusted.
     */
    private record TokenId(@CheckForNull String userId, @CheckForNull String uuid) {}

    /**
     * What the banner and the page show, from one scan of the tokens of all the users, done as of today.
     */
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public @NonNull Report getReport() {
        return scan(LocalDate.now());
    }

    /**
     * Scans the tokens of all the users once, with one {@code today} for everything: the phase, the counts and the rows
     * all come from it. The users that were never saved are scanned as well, as {@link User#getAll()} returns them, and the
     * users that have no {@link ApiTokenProperty}, or no token, are skipped.
     * <p>
     * A token is a row when it does not comply, or when it holds a secret that another token that has not expired holds too,
     * and its owner is not exempt in either case. The rows are sorted by the ID of their owner and then by the name of
     * the token, so the order in which the users are returned does not matter.
     */
    @NonNull
    Report scan(@NonNull LocalDate today) {
        ApiTokenLifetimePolicy policy = new ApiTokenLifetimePolicy();
        Phase phase = policy.getPhase(today);
        LocalDate enforcementStart = policy.getEnforcementStart();
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();

        List<Row> rows = new ArrayList<>();
        int nonCompliantCount = 0;
        int sharedSecretGroupCount = 0;
        if (phase != Phase.INACTIVE) {
            for (User user : User.getAll()) {
                ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
                if (property == null || property.getTokenStore() == null || property.getTokenStats() == null) {
                    continue;
                }
                ApiTokenStore store = property.getTokenStore();
                List<HashedToken> tokens = store.getTokenListSnapshot();
                if (tokens.isEmpty()) {
                    continue;
                }

                // The snapshot and the groups are read apart, so a token can be revoked in between: the siblings that are
                // not in the snapshot are ignored.
                Map<String, List<String>> sharing = store.getUuidsSharingSecret();
                boolean reportsSharedSecrets = !sharing.isEmpty() && policy.isMonitored(user, today);
                Map<String, HashedToken> byUuid = new HashMap<>();
                for (HashedToken token : tokens) {
                    byUuid.put(token.getUuid(), token);
                }

                Set<String> counted = new HashSet<>();
                for (HashedToken token : tokens) {
                    boolean compliant = policy.isCompliant(user, token, today);

                    String sharedWith = null;
                    List<String> liveSharedNames = List.of();
                    if (reportsSharedSecrets && !token.isExpired(today)) {
                        List<String> others = sharing.getOrDefault(token.getUuid(), List.of());
                        boolean anotherCanStillBeUsed = false;
                        List<String> names = new ArrayList<>(others.size());
                        List<String> liveNames = new ArrayList<>(others.size());
                        for (String otherUuid : others) {
                            HashedToken other = byUuid.get(otherUuid);
                            if (other == null) {
                                continue;
                            }
                            if (other.isExpired(today)) {
                                names.add(Messages.ApiTokenLifetimeAdministrativeMonitor_Sibling_Expired(Util.fixNull(other.getName())));
                            } else {
                                anotherCanStillBeUsed = true;
                                names.add(Util.fixNull(other.getName()));
                                liveNames.add(Util.fixNull(other.getName()));
                            }
                        }
                        // a secret that only one token that has not expired holds is not a finding: the others are bounded
                        if (anotherCanStillBeUsed) {
                            sharedWith = String.join(", ", names);
                            liveSharedNames = liveNames;
                            // each group is counted once, at the first of its tokens that is listed
                            if (others.stream().noneMatch(counted::contains)) {
                                sharedSecretGroupCount++;
                            }
                            counted.add(token.getUuid());
                        }
                    }

                    if (compliant && sharedWith == null) {
                        continue;
                    }
                    if (!compliant) {
                        nonCompliantCount++;
                    }
                    rows.add(new Row(user, property, token, !compliant, sharedWith, liveSharedNames, policy.maxExpiration(today)));
                }
            }
        }

        Comparator<String> byUserId = User.idStrategy()::compare;
        rows.sort(Comparator.comparing(Row::getUserId, byUserId)
                .thenComparing(row -> row.getName().toLowerCase(Locale.ENGLISH))
                .thenComparing(Row::getName));

        return new Report(phase, phaseLabel(phase, enforcementStart), countdown(phase, enforcementStart, today),
                config.getMaxTokenLifetimeDays(), config.isUsageStatisticsEnabled(),
                nonCompliantCount, sharedSecretGroupCount, rows);
    }

    private static @CheckForNull ApiTokenStore storeOf(@NonNull User user) {
        ApiTokenProperty property = user.getProperty(ApiTokenProperty.class);
        return property == null ? null : property.getTokenStore();
    }

    private static @NonNull String phaseLabel(@NonNull Phase phase, @CheckForNull LocalDate enforcementStart) {
        switch (phase) {
            case WARNING:
                return Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_Warning(String.valueOf(enforcementStart));
            case ENFORCING:
                // without a date the policy enforces from the start, there is no date to show
                return enforcementStart == null
                        ? Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_Enforcing()
                        : Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_EnforcingSince(String.valueOf(enforcementStart));
            case ENFORCING_INVALID_DATE:
                return Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_EnforcingInvalidDate();
            default:
                return Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_Inactive();
        }
    }

    /**
     * @return what remains of the warning phase, {@code null} in any other phase
     */
    private static @CheckForNull String countdown(@NonNull Phase phase, @CheckForNull LocalDate enforcementStart, @NonNull LocalDate today) {
        if (phase != Phase.WARNING || enforcementStart == null) {
            return null;
        }
        return Messages.ApiTokenLifetimeAdministrativeMonitor_Phase_Warning_Countdown(ChronoUnit.DAYS.between(today, enforcementStart));
    }

    /**
     * Everything that the banner and the page show, as of one day. It is read-only, and it holds no
     * {@code HashedToken} nor any hash: only what is displayed.
     */
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public static final class Report {
        private final Phase phase;
        private final String phaseLabel;
        private final String countdown;
        private final int maxDays;
        private final boolean usageStatisticsEnabled;
        private final int nonCompliantCount;
        private final int sharedSecretGroupCount;
        private final List<Row> rows;

        private Report(Phase phase, String phaseLabel, @CheckForNull String countdown, int maxDays, boolean usageStatisticsEnabled,
                       int nonCompliantCount, int sharedSecretGroupCount, List<Row> rows) {
            this.phase = phase;
            this.phaseLabel = phaseLabel;
            this.countdown = countdown;
            this.maxDays = maxDays;
            this.usageStatisticsEnabled = usageStatisticsEnabled;
            this.nonCompliantCount = nonCompliantCount;
            this.sharedSecretGroupCount = sharedSecretGroupCount;
            this.rows = Collections.unmodifiableList(rows);
        }

        public Phase getPhase() {
            return phase;
        }

        /**
         * Whether a maximum is configured: when it is not, there is nothing to report.
         */
        public boolean isActive() {
            return phase != Phase.INACTIVE;
        }

        /**
         * Where the policy stands, for display: for example the date of the end of the warning phase.
         */
        public String getPhaseLabel() {
            return phaseLabel;
        }

        /**
         * @return how long the warning phase lasts, for display, {@code null} when the policy is not in its warning phase
         */
        public @CheckForNull String getCountdown() {
            return countdown;
        }

        /**
         * The configured maximum lifetime, in days.
         */
        public int getMaxDays() {
            return maxDays;
        }

        /**
         * Whether the usage of the tokens is recorded. When it is not, a token that shows no use may have been used.
         */
        public boolean isUsageStatisticsEnabled() {
            return usageStatisticsEnabled;
        }

        /**
         * The number of tokens that do not comply with the maximum.
         */
        public int getNonCompliantCount() {
            return nonCompliantCount;
        }

        /**
         * The number of secrets that several tokens that have not expired hold, each one counted once.
         */
        public int getSharedSecretGroupCount() {
            return sharedSecretGroupCount;
        }

        /**
         * The tokens that do not comply and the ones that share a secret, sorted by user ID and then token name.
         */
        public List<Row> getRows() {
            return rows;
        }
    }

    /**
     * A token that the page lists. The owner and the name of the token are chosen by users: they must only be shown as
     * escaped text, or as the value of a {@code data-*} attribute.
     */
    // used by Jelly view
    @Restricted(NoExternalUse.class)
    public static final class Row {
        private final String userId;
        private final String userFullName;
        private final String userUrl;
        private final ApiTokenProperty.TokenInfoAndStats token;
        private final String expiration;
        private final boolean overMaximum;
        private final long daysOverMaximum;
        private final String sharedWith;
        private final List<String> liveSharedNames;

        /**
         * @param overMaximum whether the token does not comply with the maximum
         * @param sharedWith the names of the other tokens that hold the secret of this one, those that have expired suffixed
         *                   as such, {@code null} if this token is not listed for sharing a secret
         * @param liveSharedNames the names of those of the other tokens that have not expired, which are the ones that revoking
         *                        this token revokes as well
         * @param maxExpiration the latest expiration date that complies, {@code today} plus the maximum
         */
        private Row(User user, ApiTokenProperty property, HashedToken token, boolean overMaximum, @CheckForNull String sharedWith, List<String> liveSharedNames, LocalDate maxExpiration) {
            this.userId = user.getId();
            this.userFullName = user.getFullName();
            this.userUrl = user.getUrl();
            this.token = new ApiTokenProperty.TokenInfoAndStats(token, property.getTokenStats().findTokenStatsById(token.getUuid()));
            LocalDate expirationDate = token.getExpirationDate();
            this.expiration = expirationDate == null ? null : expirationDate.toString();
            this.overMaximum = overMaximum;
            this.daysOverMaximum = expirationDate == null ? 0 : ChronoUnit.DAYS.between(maxExpiration, expirationDate);
            this.sharedWith = sharedWith;
            this.liveSharedNames = List.copyOf(liveSharedNames);
        }

        public String getUserId() {
            return userId;
        }

        public String getUserFullName() {
            return userFullName;
        }

        /**
         * The URL of the user, relative to the root of Jenkins, already encoded.
         */
        public String getUserUrl() {
            return userUrl;
        }

        public String getUuid() {
            return token.uuid;
        }

        public String getName() {
            return Util.fixNull(token.name);
        }

        /**
         * The token with its usage, whose accessors cope with a creation date or a last use that is not known.
         */
        public ApiTokenProperty.TokenInfoAndStats getToken() {
            return token;
        }

        /**
         * Whether this is a legacy token: it never expires and cannot be given an expiration.
         */
        public boolean isLegacy() {
            return token.isLegacy;
        }

        /**
         * Whether this is a token that has no expiration date. The legacy token does not count, as it has its own status.
         */
        public boolean isNeverExpires() {
            return !token.isLegacy && expiration == null;
        }

        /**
         * @return the day through which the token is valid, as an ISO date, {@code null} if it has none
         */
        public @CheckForNull String getExpiration() {
            return expiration;
        }

        /**
         * Whether this token does not comply with the maximum on its own, whether or not it shares its secret.
         */
        public boolean isOverMaximum() {
            return overMaximum;
        }

        /**
         * How many days after the latest date that complies this token expires, if it expires and does not comply.
         */
        public long getDaysOverMaximum() {
            return daysOverMaximum;
        }

        /**
         * Whether this token holds a secret that another token that has not expired holds too.
         */
        public boolean isSharesSecret() {
            return sharedWith != null;
        }

        /**
         * @return the names of the other tokens that hold the same secret, those that have expired marked as such,
         *         {@code null} if this token does not share its secret
         */
        public @CheckForNull String getSharedWith() {
            return sharedWith;
        }

        /**
         * The sentence that names the other tokens, whole, so that what users chose is not an argument of an i18n
         * expression of the view but is shown as one escaped text, like the other values.
         *
         * @return {@code null} if this token does not share its secret
         */
        public @CheckForNull String getSharedWithMarker() {
            return sharedWith == null ? null : Messages.ApiTokenLifetimeAdministrativeMonitor_Sibling_Marker(sharedWith);
        }

        /**
         * The names of the other tokens that revoking this one revokes as well, those that hold its secret and have not expired,
         * as a JSON array of strings for a {@code data-*} attribute of the view, so that the script of the page can list them
         * in the confirmation. Names are chosen by users and may hold any character: a JSON array has no separator that a name
         * could imitate. It is {@code []} when this token does not share its secret.
         */
        public String getLiveSharedNamesJson() {
            return JSONArray.fromObject(liveSharedNames).toString();
        }

        /**
         * Whether the expiration of this token can be made earlier: it does not comply on its own and it is not the legacy
         * token, which has no expiration to change.
         */
        public boolean isShortenable() {
            return overMaximum && !token.isLegacy;
        }
    }
}
