/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import com.google.common.cache.Cache;
import static com.google.common.cache.CacheBuilder.newBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.security.UserMayOrMayNotExistException2;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Cache layer for {@link UserDetails} lookup.
 */
@Restricted(NoExternalUse.class)
@Extension
public final class UserDetailsCache {

    private static final String SYS_PROP_NAME = UserDetailsCache.class.getName() + ".EXPIRE_AFTER_WRITE_SEC";
    /**
     * Nr of seconds before a value expires after being cached, note full GC will also clear the cache.
     * Should be able to set this value in script and then reload from disk to change in runtime.
     */
    private static /*not final*/ Integer EXPIRE_AFTER_WRITE_SEC = SystemProperties.getInteger(SYS_PROP_NAME, (int)TimeUnit.MINUTES.toSeconds(2));
    private final Cache<String, UserDetails> detailsCache;
    private final Cache<String, Boolean> existenceCache;

    /**
     * Constructor intended to be instantiated by Jenkins only.
     */
    @Restricted(NoExternalUse.class)
    public UserDetailsCache() {
        if (EXPIRE_AFTER_WRITE_SEC == null || EXPIRE_AFTER_WRITE_SEC <= 0) {
            //just in case someone is trying to trick us
            EXPIRE_AFTER_WRITE_SEC = SystemProperties.getInteger(SYS_PROP_NAME, (int)TimeUnit.MINUTES.toSeconds(2));
            if (EXPIRE_AFTER_WRITE_SEC <= 0) {
                //The property could also be set to a negative value
                EXPIRE_AFTER_WRITE_SEC = (int)TimeUnit.MINUTES.toSeconds(2);
            }
        }
        detailsCache = newBuilder().softValues().expireAfterWrite(EXPIRE_AFTER_WRITE_SEC, TimeUnit.SECONDS).build();
        existenceCache = newBuilder().softValues().expireAfterWrite(EXPIRE_AFTER_WRITE_SEC, TimeUnit.SECONDS).build();
    }

    /**
     * The singleton instance registered in Jenkins.
     * @return the cache
     */
    public static UserDetailsCache get() {
        return ExtensionList.lookupSingleton(UserDetailsCache.class);
    }

    /**
     * Gets the cached UserDetails for the given username.
     * Similar to {@link #loadUserByUsername(String)} except it doesn't perform the actual lookup if there is a cache miss.
     *
     * @param idOrFullName the username
     *
     * @return {@code null} if the cache doesn't contain any data for the key or the user details cached for the key.
     * @throws UsernameNotFoundException if a previous lookup resulted in the same
     */
    @CheckForNull
    public UserDetails getCached(String idOrFullName) throws UsernameNotFoundException {
        Boolean exists = existenceCache.getIfPresent(idOrFullName);
        if (exists != null && !exists) {
            throw new UserMayOrMayNotExistException2(String.format("\"%s\" does not exist", idOrFullName));
        } else {
            return detailsCache.getIfPresent(idOrFullName);
        }
    }

    /**
     * Locates the user based on the username, by first looking in the cache and then delegate to
     * {@link hudson.security.SecurityRealm#loadUserByUsername2(String)}.
     *
     * @param idOrFullName the username
     * @return the details
     *
     * @throws UsernameNotFoundException (normally a {@link hudson.security.UserMayOrMayNotExistException2})
     *              if the user could not be found or the user has no GrantedAuthority
     * @throws ExecutionException if anything else went wrong in the cache lookup/retrieval
     */
    @NonNull
    public UserDetails loadUserByUsername(String idOrFullName) throws UsernameNotFoundException, ExecutionException {
        Boolean exists = existenceCache.getIfPresent(idOrFullName);
        if(exists != null && !exists) {
            throw new UsernameNotFoundException(String.format("\"%s\" does not exist", idOrFullName));
        } else {
            try {
                return detailsCache.get(idOrFullName, new Retriever(idOrFullName));
            } catch (ExecutionException | UncheckedExecutionException e) {
                if (e.getCause() instanceof UsernameNotFoundException) {
                    throw ((UsernameNotFoundException)e.getCause());
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Discards all entries in the cache.
     */
    public void invalidateAll() {
        existenceCache.invalidateAll();
        detailsCache.invalidateAll();
    }

    /**
     * Discards any cached value for key.
     * @param idOrFullName the key
     */
    public void invalidate(final String idOrFullName) {
        existenceCache.invalidate(idOrFullName);
        detailsCache.invalidate(idOrFullName);
    }

    /**
     * Callable that performs the actual lookup if there is a cache miss.
     * @see #loadUserByUsername(String)
     */
    private class Retriever implements Callable<UserDetails> {
        private final String idOrFullName;

        private Retriever(final String idOrFullName) {
            this.idOrFullName = idOrFullName;
        }

        @Override
        public UserDetails call() throws Exception {
            try {
                Jenkins jenkins = Jenkins.get();
                UserDetails userDetails = jenkins.getSecurityRealm().loadUserByUsername2(idOrFullName);
                if (userDetails == null) {
                    existenceCache.put(this.idOrFullName, Boolean.FALSE);
                    throw new NullPointerException("hudson.security.SecurityRealm should never return null. "
                                                   + jenkins.getSecurityRealm() + " returned null for idOrFullName='" + idOrFullName + "'");
                }
                existenceCache.put(this.idOrFullName, Boolean.TRUE);
                return userDetails;
            } catch (UsernameNotFoundException e) {
                existenceCache.put(this.idOrFullName, Boolean.FALSE);
                throw e;
            }
        }
    }
}
