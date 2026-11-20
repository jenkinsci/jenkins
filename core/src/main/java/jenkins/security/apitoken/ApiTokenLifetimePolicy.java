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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.User;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.IdStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Rules for the maximum lifetime of the API tokens that are created through the HTTP endpoints, as configured in
 * {@link ApiTokenPropertyConfiguration}, and, in strict mode, through the Java API as well. This class holds no state of
 * its own: it reads the configuration on each call.
 * <p>
 * Every method that depends on the date takes {@code today} as a parameter, so there is no hidden clock.
 * <ul>
 * <li>The policy is <em>active</em> when the maximum is greater than 0 days. Otherwise nothing is restricted.
 * <li>An active policy is <em>enforcing</em> once the enforcement date is reached (that day included).
 * Without a date, or with a blank one, it enforces right away. Before the date the policy is in its
 * <em>warning phase</em>: it is active, but it does not reject anything. A date that cannot be parsed fails closed: the
 * policy enforces right away and a WARNING is logged when the value differs from the last one that was logged. The form
 * rejects such a date, but the setter stores it as is, so it can still come from a script, from configuration as code
 * or from a configuration file.
 * <li>The users listed in {@link ApiTokenPropertyConfiguration#getExemptUserIds()} are <em>exempt</em>: the policy never
 * rejects a token of theirs. The list is read as it is stored, one user ID per line, each line trimmed and the empty
 * lines skipped, and the IDs are compared with the {@link IdStrategy} of {@link User#idStrategy()}.
 * <li>When the policy is enforcing and the token owner is not exempt, a token must expire, and no later than
 * {@link #maxExpiration(LocalDate) today plus the maximum}. A token is valid through its expiration date, so that
 * day itself is accepted.
 * <li>The tokens that exist already are only reported, by {@link ApiTokenLifetimeAdministrativeMonitor}, and in every phase,
 * the warning phase included: see {@link #isCompliant} and {@link #isMonitored}.
 * <li>The <em>strict mode</em>, see {@link #isStrict(LocalDate)}, is a switch of the configuration that has an effect once the
 * policy is enforcing. Then nobody is exempt: {@link #isExempt} is false for every owner, so the rules above cover every
 * owner. And the maximum is checked by {@link ApiTokenStore} too, which is what the Java API goes through, and by
 * {@link jenkins.security.ApiTokenProperty#changeApiToken()}. Without the strict mode only the HTTP endpoints check it.
 * Until the enforcement date, when it is {@linkplain #isStrictScheduled(LocalDate) scheduled}, the exemptions still apply, but
 * the tokens of the exempt owners that exceed the maximum are reported already: see {@link #getExemptionEnd}.
 * </ul>
 */
@Restricted(NoExternalUse.class)
public class ApiTokenLifetimePolicy {

    /**
     * Why a token does not comply with the policy.
     */
    public enum Violation {
        /** The token would never expire. */
        NEVER_NOT_ALLOWED,
        /** The token would expire after {@link ApiTokenLifetimePolicy#maxExpiration(LocalDate)}. */
        OVER_MAXIMUM
    }

    /**
     * Where the policy stands on a given day, which is what the compliance panel shows.
     */
    public enum Phase {
        /** No maximum is configured. */
        INACTIVE,
        /** A maximum is configured and the enforcement date has not been reached: nothing is rejected yet. */
        WARNING,
        /** The maximum is enforced, from the configured date or, with no date, from the start. */
        ENFORCING,
        /** The maximum is enforced because the configured enforcement date cannot be parsed, which fails closed. */
        ENFORCING_INVALID_DATE
    }

    private static final Logger LOGGER = Logger.getLogger(ApiTokenLifetimePolicy.class.getName());

    private static final Pattern NEWLINE = Pattern.compile("\r\n|\r|\n");

    /**
     * Stands for an enforcement date that cannot be parsed: before any possible {@code today}, so already reached.
     */
    private static final LocalDate UNPARSABLE = LocalDate.MIN;

    /**
     * The last invalid enforcement date that was logged, so that the same value is not logged again on every request.
     */
    private static final AtomicReference<String> LAST_WARNED_ENFORCE_FROM = new AtomicReference<>();

    /**
     * Whether a maximum lifetime is configured.
     */
    public boolean isActive() {
        return config().getMaxTokenLifetimeDays() > 0;
    }

    /**
     * Whether the maximum lifetime applies today, that is whether the policy is active and its enforcement date,
     * if any, has been reached.
     */
    public boolean isEnforcing(@NonNull LocalDate today) {
        if (!isActive()) {
            return false;
        }
        LocalDate enforceFrom = parseEnforceFrom();
        return enforceFrom == null || !today.isBefore(enforceFrom);
    }

    /**
     * @return the date from which the policy will be enforced if it is active and in its warning phase,
     *         otherwise {@code null}
     */
    @CheckForNull
    public LocalDate getPendingEnforceFrom(@NonNull LocalDate today) {
        if (!isActive()) {
            return null;
        }
        LocalDate enforceFrom = parseEnforceFrom();
        return enforceFrom != null && today.isBefore(enforceFrom) ? enforceFrom : null;
    }

    /**
     * Where the policy stands on {@code today}: whether it is active and, if it is, whether it is still in its warning
     * phase or enforcing, and whether it enforces because its enforcement date does not parse.
     */
    @NonNull
    public Phase getPhase(@NonNull LocalDate today) {
        if (!isActive()) {
            return Phase.INACTIVE;
        }
        LocalDate enforceFrom = parseEnforceFrom();
        if (UNPARSABLE.equals(enforceFrom)) {
            return Phase.ENFORCING_INVALID_DATE;
        }
        return enforceFrom != null && today.isBefore(enforceFrom) ? Phase.WARNING : Phase.ENFORCING;
    }

    /**
     * @return the configured date from which the policy is, or was, enforced if the policy is active, otherwise
     *         {@code null}, which is also the result when no date is configured, in which case it enforces right away,
     *         and when the date does not parse. It does not say whether that date has been reached, see
     *         {@link #getPhase(LocalDate)}.
     */
    @CheckForNull
    public LocalDate getEnforcementStart() {
        if (!isActive()) {
            return null;
        }
        LocalDate enforceFrom = parseEnforceFrom();
        return UNPARSABLE.equals(enforceFrom) ? null : enforceFrom;
    }

    /**
     * Whether the strict mode is in effect: it is switched on in the configuration, and the maximum lifetime is enforced
     * ({@link #isEnforcing(LocalDate)}), so a strict mode that is switched on has no effect while there is no maximum, nor in
     * the warning phase. A stored enforcement date that cannot be parsed counts as an enforcement that has started, as for
     * {@code isEnforcing}, so the strict mode is in effect from the start in that case too.
     * <p>
     * While it is in effect nobody is {@linkplain #isExempt exempt}, and {@link ApiTokenStore} refuses the tokens that break the
     * maximum, see {@link ApiTokenPolicyViolationException}.
     */
    public boolean isStrict(@NonNull LocalDate today) {
        // the switch first: it costs nothing, and it is off unless an administrator chose otherwise
        return config().isStrict() && isEnforcing(today);
    }

    /**
     * Whether the strict mode is switched on and will take effect later: a maximum is configured, and its enforcement date
     * has not been reached, so that it is in its warning phase. It is the counterpart of {@link #isStrict(LocalDate)}: on
     * any given day at most one of the two holds, and neither does while the strict mode is switched off or no maximum is
     * configured.
     */
    public boolean isStrictScheduled(@NonNull LocalDate today) {
        return config().isStrict() && getPendingEnforceFrom(today) != null;
    }

    /**
     * Tells whether the owner is exempt now but will not be once the strict mode starts, and from when. While the strict mode
     * is {@linkplain #isStrictScheduled(LocalDate) scheduled}, the exemptions still apply, and they stop on the enforcement date,
     * as everybody is held to the maximum from then on. This is what lets the compliance panel list the tokens of such an owner
     * that will not comply, before that day.
     *
     * @return the day on which the strict mode starts, and with it the end of the exemption, if the strict mode is scheduled and
     *         the owner is exempt; {@code null} in every other case: the strict mode is switched off, already in effect (nobody
     *         is exempt then), or has no maximum to apply, or the owner is not exempt
     */
    @CheckForNull
    public LocalDate getExemptionEnd(@NonNull User owner, @NonNull LocalDate today) {
        // the switch first, as for isStrict: this runs for each user
        if (!config().isStrict()) {
            return null;
        }
        LocalDate strictStart = getPendingEnforceFrom(today);
        return strictStart != null && isExempt(owner, today) ? strictStart : null;
    }

    /**
     * Whether the owner of a token is exempt from the maximum lifetime. Nobody is while the strict mode is
     * {@linkplain #isStrict(LocalDate) in effect}: the exemptions stay in the configuration, and apply again when it is not.
     *
     * @param today the day that decides whether the strict mode is in effect
     */
    public boolean isExempt(@NonNull User owner, @NonNull LocalDate today) {
        if (isStrict(today)) {
            return false;
        }
        IdStrategy strategy = User.idStrategy();
        for (String id : parseUserIds(config().getExemptUserIds())) {
            if (strategy.equals(id, owner.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the maximum lifetime applies to the tokens of this owner, that is whether the policy is
     * {@linkplain #isEnforcing(LocalDate) enforcing} and the owner is not {@linkplain #isExempt(User, LocalDate) exempt}.
     * Callers ask this instead of combining the two. The strict mode needs no case of its own here: while it is
     * {@linkplain #isStrict(LocalDate) in effect} nobody is exempt, so this holds for every owner as soon as the policy
     * is enforcing.
     */
    public boolean appliesTo(@NonNull User owner, @NonNull LocalDate today) {
        return isEnforcing(today) && !isExempt(owner, today);
    }

    /**
     * Whether the tokens of this owner that exist are checked against the maximum lifetime, which is what the compliance
     * panel reports on. Unlike {@link #appliesTo}, which decides whether a token that is created is refused, this holds in
     * every phase: it is true as soon as the policy is {@linkplain #isActive() active}, in its warning phase too, and the
     * owner is not {@linkplain #isExempt(User, LocalDate) exempt}.
     */
    public boolean isMonitored(@NonNull User owner, @NonNull LocalDate today) {
        return isActive() && !isExempt(owner, today);
    }

    /**
     * The latest expiration date that a token created today may have: {@code today} plus the maximum.
     * Only meaningful when the policy is {@linkplain #isActive() active}.
     */
    @NonNull
    public LocalDate maxExpiration(@NonNull LocalDate today) {
        return today.plusDays(config().getMaxTokenLifetimeDays());
    }

    /**
     * Checks the expiration date of a token that is about to be created.
     *
     * @param expiration the date through which the token will be valid, {@code null} if it never expires
     * @return {@code null} if the token is acceptable, which includes every token while the policy is not enforcing and
     *         every token of an exempt owner, otherwise the reason why it is not
     */
    @CheckForNull
    public Violation validate(@NonNull User owner, @CheckForNull LocalDate expiration, @NonNull LocalDate today) {
        if (!appliesTo(owner, today)) {
            return null;
        }
        if (expiration == null) {
            return Violation.NEVER_NOT_ALLOWED;
        }
        if (expiration.isAfter(maxExpiration(today))) {
            return Violation.OVER_MAXIMUM;
        }
        return null;
    }

    /**
     * Checks a token that exists, which the compliance panel does. Unlike {@link #validate}, it does not depend on whether the
     * policy is enforcing: a token is checked in every phase, the warning phase included, as soon as a maximum is configured.
     * <p>
     * A token is not compliant when the policy is {@linkplain #isActive() active}, it has not expired on {@code today}, and
     * it never expires, which is the case of the legacy token, or expires after {@link #maxExpiration(LocalDate) today plus
     * the maximum}. A token that expires on that day is compliant, and so is one that has expired already: it cannot be used.
     * The tokens of an {@linkplain #isExempt(User, LocalDate) exempt} owner are compliant whatever their expiration, as for
     * {@link #validate}.
     *
     * @param owner the user who owns the token
     * @param today the day that decides whether the token has expired and how far the maximum reaches
     */
    public boolean isCompliant(@NonNull User owner, @NonNull ApiTokenStore.HashedToken token, @NonNull LocalDate today) {
        if (!exceedsMaximum(token, today)) {
            return true;
        }
        // only a token that exceeds the maximum depends on the owner, so the list of exemptions is read for these only
        return isExempt(owner, today);
    }

    /**
     * Checks a token that exists against the maximum alone, whoever owns it: whether the policy is
     * {@linkplain #isActive() active}, the token has not expired on {@code today}, and it never expires, which is the case of
     * the legacy token, or expires after {@link #maxExpiration(LocalDate) today plus the maximum}. It is the question that
     * {@link #isCompliant} asks before the exemptions, and that the compliance panel asks for the tokens of the owners who are
     * exempt only until the strict mode starts, see {@link #getExemptionEnd}.
     *
     * @param today the day that decides whether the token has expired and how far the maximum reaches
     */
    public boolean exceedsMaximum(@NonNull ApiTokenStore.HashedToken token, @NonNull LocalDate today) {
        if (!isActive()) {
            return false;
        }
        // a token that has expired expires before today, so it is not after the maximum either
        LocalDate expiration = token.getExpirationDate();
        return expiration == null || expiration.isAfter(maxExpiration(today));
    }

    /**
     * Splits a configured list of user IDs: one ID per line, each trimmed, the empty lines skipped. It splits on line
     * breaks only, so that an ID that contains spaces is kept whole.
     */
    @NonNull
    static List<String> parseUserIds(@CheckForNull String userIds) {
        List<String> ids = new ArrayList<>();
        if (userIds != null) {
            for (String line : NEWLINE.split(userIds)) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * Parses the enforcement date. This is the only place where it is parsed.
     *
     * @return {@code null} when it is not set or blank, {@link #UNPARSABLE} when it is not an ISO date, in which case a
     *         warning is logged if this value differs from the last one that was logged, otherwise the date
     */
    @CheckForNull
    private LocalDate parseEnforceFrom() {
        // Descriptor#load does not call the setter: a blank value can be stored if the file was edited
        String raw = Util.fixEmptyAndTrim(config().getEnforceFrom());
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            if (!raw.equals(LAST_WARNED_ENFORCE_FROM.getAndSet(raw))) {
                // the value comes from a file or a script: no control characters in the log
                LOGGER.log(Level.WARNING, "The API token enforcement date \"{0}\" is not an ISO date (yyyy-MM-dd). The maximum lifetime is enforced right away.", raw.replaceAll("\\p{Cntrl}", "?"));
            }
            return UNPARSABLE;
        }
    }

    private static ApiTokenPropertyConfiguration config() {
        return ApiTokenPropertyConfiguration.get();
    }
}
