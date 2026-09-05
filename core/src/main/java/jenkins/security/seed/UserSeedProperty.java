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

package jenkins.security.seed;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.security.LastGrantedAuthoritiesProperty;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * The seed stored in this property is used to have a revoke feature on the session
 * without having to hack the session management that depends on the application server used to run the instance.
 *
 * The seed is added to the session when a user just logged in and then for every request,
 * before using the session information, we check the seed was not changed in the meantime.
 *
 * This feature allows the admin to revoke all the sessions that are in the wild without having to keep a list of them.
 *
 * @see hudson.security.AuthenticationProcessingFilter2 for the addition of seed inside the session
 * @see hudson.security.HttpSessionContextIntegrationFilter2 for the seed check from the session before using it
 */
public class UserSeedProperty extends UserProperty {
    /**
     * Escape hatch for User seed based revocation feature.
     * If we disable the seed, we can still use it to write / store information but not verifying the data using it.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean DISABLE_USER_SEED = SystemProperties.getBoolean(UserSeedProperty.class.getName() + ".disableUserSeed");

    /**
     * Hide the user seed section from the UI to prevent accidental use
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean HIDE_USER_SEED_SECTION = SystemProperties.getBoolean(UserSeedProperty.class.getName() + ".hideUserSeedSection");

    public static final String USER_SESSION_SEED = "_JENKINS_SESSION_SEED";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SEED_NUM_BYTES = 8;

    private String seed;

    private UserSeedProperty() {
        this.renewSeedInternal();
    }

    public @NonNull String getSeed() {
        return seed;
    }

    public void renewSeed() {
        this.renewSeedInternal();

        UserSeedChangeListener.fireUserSeedRenewed(this.user);
    }

    private void renewSeedInternal() {
        String currentSeed = this.seed;
        String newSeed = currentSeed;
        byte[] bytes = new byte[SEED_NUM_BYTES];
        while (Objects.equals(newSeed, currentSeed)) {
            RANDOM.nextBytes(bytes);
            newSeed = Util.toHexString(bytes);
        }
        this.seed = newSeed;
    }

    @Extension
    @Symbol("userSeed")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @Override
        public @NonNull String getDisplayName() {
            return Messages.UserSeedProperty_DisplayName();
        }

        @Override
        public UserSeedProperty newInstance(User user) {
            return new UserSeedProperty();
        }

        // only for jelly
        @Restricted(DoNotUse.class)
        public boolean isCurrentUser(@NonNull User target) {
            return Objects.equals(User.current(), target);
        }

        @RequirePOST
        public synchronized HttpResponse doRenewSessionSeed(@AncestorInPath @NonNull User u) throws IOException {
            u.checkPermission(Jenkins.ADMINISTER);

            if (DISABLE_USER_SEED) {
                return HttpResponses.error(404, "User seed feature is disabled");
            }

            try (BulkChange bc = new BulkChange(u)) {
                UserSeedProperty p = u.getProperty(UserSeedProperty.class);
                p.renewSeed();

                LastGrantedAuthoritiesProperty lastGranted = u.getProperty(LastGrantedAuthoritiesProperty.class);
                if (lastGranted != null) {
                    lastGranted.invalidate();
                }

                bc.commit();
            }

            return HttpResponses.ok();
        }

        @Override
        public boolean isEnabled() {
            return !DISABLE_USER_SEED && !HIDE_USER_SEED_SECTION;
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Security.class);
        }
    }
}
