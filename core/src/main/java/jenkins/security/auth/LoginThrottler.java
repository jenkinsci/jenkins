/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package jenkins.security.auth;

import hudson.Extension;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import jenkins.util.TimeProvider;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.userdetails.UserDetails;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class LoginThrottler extends SecurityListener {
    private static final Logger LOGGER = Logger.getLogger(LoginThrottler.class.getName());

    private static final Integer RATE_LIMITER_WINDOW_DURATION = Integer.getInteger(LoginThrottler.class.getName() + ".duration", 30);
    private static final Integer RATE_LIMITER_MAX_ATTEMPTS = Integer.getInteger(LoginThrottler.class.getName() + ".attempts", 3);

    private ReadWriteLock failuresLock = new ReentrantReadWriteLock();
    private Map<String, UserLoginFailuresInfo> failures = new HashMap<>();

    private static class UserLoginFailuresInfo {
        private SortedSet<Date> failures;

        private String name;

        private UserLoginFailuresInfo(String name) {
            failures = new TreeSet<>();
            this.name = name;
        }

        public void recordFailure() {
            LOGGER.log(Level.FINE, () -> "Recording login failure for " + name);

            long nowTime = TimeProvider.get().getCurrentTimeMillis();
            Date now = new Date(nowTime);
            failures.add(now);

            // remove login failures from earlier than the relevant rate limiting window
            Date ago = new Date(nowTime);
            ago.setTime(ago.getTime() - RATE_LIMITER_WINDOW_DURATION * 1000);
            failures = failures.tailSet(ago);

            LOGGER.log(Level.FINE, () -> "There are now " + failures.size() + " recorded login failures for " + name);
        }

        public boolean isSevereFailureRate() {
            long nowTime = TimeProvider.get().getCurrentTimeMillis();
            Date ago = new Date(nowTime);
            ago.setTime(ago.getTime() - RATE_LIMITER_WINDOW_DURATION * 1000);
            int failureCountInTimeSpan = failures.tailSet(ago).size();

            LOGGER.log(Level.FINE, () -> "Determining failure rate for " + name + " at " + failureCountInTimeSpan + " of " + RATE_LIMITER_MAX_ATTEMPTS + " in " + RATE_LIMITER_WINDOW_DURATION + " seconds since: " + ago.toString());

            if (failureCountInTimeSpan >= RATE_LIMITER_MAX_ATTEMPTS) {
                return true;
            }
            return false;
        }
    }

    public static LoginThrottler get() {
        return Jenkins.get().getExtensionList(SecurityListener.class).get(LoginThrottler.class);
    }

    public List<String> getUsersWithFailures() {
        failuresLock.readLock().lock();
        try {
            return new ArrayList<>(failures.keySet());
        } finally {
            failuresLock.readLock().unlock();
        }
    }

    public List<String> getUsersCurrentlyLocked() {
        failuresLock.readLock().lock();
        try {
            return failures.entrySet().stream()
                    .filter(e -> e.getValue().isSevereFailureRate())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } finally {
            failuresLock.readLock().unlock();
        }
    }

    public void beforeAuthentication(String username) {
        this.preventAuthenticationIfSevereFailure(username);
    }
    
    public void afterSuccessfulAuthentication(String username){
        this.clearPreviousFailure(username);
    }

    @Override
    protected void authenticated(@Nonnull UserDetails userDetails) {
//        this.preventAuthenticationIfSevereFailure(userDetails.getUsername());
    }

    @Override
    protected void failedToAuthenticate(@Nonnull String username) {
        failuresLock.writeLock().lock();
        try {
            UserLoginFailuresInfo failuresInfo = failures.get(username);
            if (failuresInfo == null) {
                LOGGER.log(Level.FINE, () -> "Initializing login failure info for " + username);
                failuresInfo = new UserLoginFailuresInfo(username);
                failures.put(username, failuresInfo);
            } else {
                LOGGER.log(Level.FINE, () -> "Recording failure for " + username);
            }
            failuresInfo.recordFailure();
        } finally {
            failuresLock.writeLock().unlock();
        }
    }

    @Override
    protected void loggedIn(@Nonnull String username) {
        this.clearPreviousFailure(username);
    }

    private void preventAuthenticationIfSevereFailure(String username) {
        failuresLock.readLock().lock();
        try {
            UserLoginFailuresInfo failuresInfo = failures.get(username);
            if (failuresInfo == null) {
                return;
            }
            if (failuresInfo.isSevereFailureRate()) {
                LOGGER.log(Level.WARNING, () -> "LoginRateLimiter prevented login of " + username);
                throw new BadCredentialsException(username);
            }
        } finally {
            failuresLock.readLock().unlock();
        }
    }

    private void clearPreviousFailure(String username) {
        failuresLock.writeLock().lock();
        try {
            LOGGER.log(Level.FINE, () -> "Clearing login failure info for " + username);
            failures.remove(username);
        } finally {
            failuresLock.writeLock().unlock();
        }
    }
}
