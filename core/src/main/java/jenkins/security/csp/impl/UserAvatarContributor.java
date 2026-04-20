/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp.impl;

import hudson.Extension;
import hudson.model.User;
import hudson.tasks.UserAvatarResolver;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.navigation.UserAction;
import jenkins.security.csp.AvatarContributor;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.Directive;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * This extension automatically allows loading images from the domain hosting the current user's avatar
 * as determined via {@link hudson.tasks.UserAvatarResolver}.
 * Note that this will not make avatars of other users work, if they're from different domains.
 */
@Restricted(NoExternalUse.class)
@Extension
public class UserAvatarContributor implements Contributor {

    private static final Logger LOGGER = Logger.getLogger(UserAvatarContributor.class.getName());

    @Override
    public void apply(CspBuilder cspBuilder) {
        User user = User.current();
        if (user == null) {
            return;
        }
        final String url = UserAvatarResolver.resolveOrNull(user, UserAction.AVATAR_SIZE);
        if (url == null) {
            LOGGER.log(Level.FINE, "No avatar image found for user " + user.getId());
            return;
        }

        cspBuilder.add(Directive.IMG_SRC, AvatarContributor.extractDomainFromUrl(url));
    }
}
