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
 * {@link ApiTokenPropertyConfiguration}. This class holds no state of its own: it reads the configuration on each call.
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
     * Whether the owner of a token is exempt from the maximum lifetime.
     *
     * @param today not used yet, a strict mode will ignore the exemptions from some date on
     */
    public boolean isExempt(@NonNull User owner, @NonNull LocalDate today) {
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
     * Callers ask this instead of combining the two, so that a strict mode which will ignore the exemptions from some
     * date on has only one place to change.
     */
    public boolean appliesTo(@NonNull User owner, @NonNull LocalDate today) {
        return isEnforcing(today) && !isExempt(owner, today);
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
