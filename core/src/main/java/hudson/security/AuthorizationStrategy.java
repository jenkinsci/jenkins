/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Tom Huybrechts
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

package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.User;
import hudson.model.View;
import hudson.agents.Cloud;
import hudson.util.DescriptorList;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.IComputer;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerAccessibleType;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Controls authorization throughout Hudson.
 *
 * <h2>Persistence</h2>
 * <p>
 * This object will be persisted along with {@link jenkins.model.Jenkins} object.
 * Hudson by itself won't put the ACL returned from {@link #getRootACL()} into the serialized object graph,
 * so if that object contains state and needs to be persisted, it's the responsibility of
 * {@link AuthorizationStrategy} to do so (by keeping them in an instance field.)
 *
 * <h2>Re-configuration</h2>
 * <p>
 * The corresponding {@link Describable} instance will be asked to create a new {@link AuthorizationStrategy}
 * every time the system configuration is updated. Implementations that keep more state in ACL beyond
 * the system configuration should use {@link jenkins.model.Jenkins#getAuthorizationStrategy()} to talk to the current
 * instance to carry over the state.
 *
 * @author Kohsuke Kawaguchi
 * @see SecurityRealm
 */
@StaplerAccessibleType
public abstract class AuthorizationStrategy extends AbstractDescribableImpl<AuthorizationStrategy> implements ExtensionPoint {
    /**
     * Returns the instance of {@link ACL} where all the other {@link ACL} instances
     * for all the other model objects eventually delegate.
     * <p>
     * IOW, this ACL will have the ultimate say on the access control.
     */
    public abstract @NonNull ACL getRootACL();

    /**
     * @deprecated since 1.277
     *      Override {@link #getACL(Job)} instead.
     */
    @Deprecated
    public @NonNull ACL getACL(@NonNull AbstractProject<?, ?> project) {
        return getACL((Job) project);
    }

    public @NonNull ACL getACL(@NonNull Job<?, ?> project) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different views.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation makes the view visible if any of the items are visible
     * or the view is configurable.
     *
     * @since 1.220
     */
    public @NonNull ACL getACL(final @NonNull View item) {
        return ACL.lambda2((a, permission) -> {
                ACL base = item.getOwner().getACL();

                boolean hasPermission = base.hasPermission2(a, permission);
                if (!hasPermission && permission == View.READ) {
                    return base.hasPermission2(a, View.CONFIGURE) || !item.getItems().isEmpty();
                }

                return hasPermission;
        });
    }

    /**
     * Implementation can choose to provide different ACL for different items.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.220
     */
    public @NonNull ACL getACL(@NonNull AbstractItem item) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL per user.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.221
     */
    public @NonNull ACL getACL(@NonNull User user) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different computers.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation delegates to {@link #getACL(Node)}
     *
     * @since 1.220
     */
    public @NonNull ACL getACL(@NonNull Computer computer) {
        return getACL(computer.getNode());
    }

    /**
     * Implementation can choose to provide different ACL for different computers.
     * This can be used as a basis for more fine-grained access control.
     * <p>
     * Default implementation delegates to {@link #getACL(Computer)} if the computer is an instance of {@link Computer},
     * otherwise it will fall back to {@link #getRootACL()}.
     *
     * @since 2.480
     **/
    public @NonNull ACL getACL(@NonNull IComputer computer) {
        if (computer instanceof Computer c) {
            return getACL(c);
        }
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different {@link Cloud}s.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * @since 1.252
     */
    public @NonNull ACL getACL(@NonNull Cloud cloud) {
        return getRootACL();
    }

    public @NonNull ACL getACL(@NonNull Node node) {
        return getRootACL();
    }

    /**
     * Returns the list of all group/role names used in this authorization strategy,
     * and the ACL returned from the {@link #getRootACL()} method.
     * <p>
     * This method is used by {@link ContainerAuthentication} to work around the servlet API issue
     * that prevents us from enumerating roles that the user has.
     * <p>
     * If such enumeration is impossible, do the best to list as many as possible, then
     * return it. In the worst case, just return an empty list. Doing so would prevent
     * users from using role names as group names (see JENKINS-2716 for such one such report.)
     *
     * @return
     *      never null.
     */
    public abstract @NonNull Collection<String> getGroups();

    /**
     * Returns all the registered {@link AuthorizationStrategy} descriptors.
     */
    public static @NonNull DescriptorExtensionList<AuthorizationStrategy, Descriptor<AuthorizationStrategy>> all() {
        return Jenkins.get().getDescriptorList(AuthorizationStrategy.class);
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     *
     * @deprecated since 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<AuthorizationStrategy> LIST = new DescriptorList<>(AuthorizationStrategy.class);

    /**
     * {@link AuthorizationStrategy} that implements the semantics
     * of unsecured Hudson where everyone has full control.
     *
     * <p>
     * This singleton is safe because {@link Unsecured} is stateless.
     */
    public static final AuthorizationStrategy UNSECURED = new Unsecured();

    public static final class Unsecured extends AuthorizationStrategy implements Serializable {
        /**
         * Maintains the singleton semantics.
         */
        private Object readResolve() {
            return UNSECURED;
        }

        @Override
        public @NonNull ACL getRootACL() {
            return UNSECURED_ACL;
        }

        @Override
        public @NonNull Collection<String> getGroups() {
            return Collections.emptySet();
        }

        private static final ACL UNSECURED_ACL = ACL.lambda2((a, p) -> true);

        @Extension @Symbol("unsecured")
        public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.AuthorizationStrategy_DisplayName();
            }

            @Override
            public @NonNull AuthorizationStrategy newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
                return UNSECURED;
            }
        }
    }
}
