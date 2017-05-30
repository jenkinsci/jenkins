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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.util.DescriptorList;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

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
public abstract class AuthorizationStrategy extends AbstractDescribableImpl<AuthorizationStrategy> implements ExtensionPoint {
    /**
     * Returns the instance of {@link ACL} where all the other {@link ACL} instances
     * for all the other model objects eventually delegate.
     * <p>
     * IOW, this ACL will have the ultimate say on the access control.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link Jenkins#getACL()} instead.
     */
    public abstract @Nonnull ACL getRootACL();

    /**
     * @deprecated since 1.277
     *      Override {@link #getACL(Job)} instead.
     */
    @Deprecated
    public @Nonnull ACL getACL(@Nonnull AbstractProject<?,?> project) {
    	return getACL((Job)project);
    }

    /**
     * Returns the instance of {@link ACL} for a job.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link Job#getACL()} instead.
     *
     * @param project a job to test access controls
     * @return access controls for the job
     */
    public @Nonnull ACL getACL(@Nonnull Job<?,?> project) {
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
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link View#getACL()} instead.
     *
     * @param item a view to test access controls
     * @return access controls for the view
     *
     * @since 1.220
     */
    public @Nonnull ACL getACL(final @Nonnull View item) {
        return new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                ACL base = item.getOwner().getACL();

                boolean hasPermission = base.hasPermission(a, permission);
                if (!hasPermission && permission == View.READ) {
                    return base.hasPermission(a,View.CONFIGURE) || !item.getItems().isEmpty();
                }

                return hasPermission;
            }
        };
    }
    
    /**
     * Implementation can choose to provide different ACL for different items.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link AbstractItem#getACL()} instead.
     * 
     * @param item an item to test access controls
     * @return access controls for the item
     *
     * @since 1.220
     */
    public @Nonnull ACL getACL(@Nonnull AbstractItem item) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL per user.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link User#getACL()} instead.
     *
     * @param user a user to test access controls
     * @return access controls for the user
     *
     * @since 1.221
     */
    public @Nonnull ACL getACL(@Nonnull User user) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different computers.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation delegates to {@link #getACL(Node)}
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link Computer#getACL()} instead.
     *
     * @param computer a computer to test access controls
     * @return access controls for the computer
     *
     * @since 1.220
     */
    public @Nonnull ACL getACL(@Nonnull Computer computer) {
        return getACL(computer.getNode());
    }

    /**
     * Implementation can choose to provide different ACL for different {@link Cloud}s.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link Cloud#getACL()} instead.
     *
     * @param cloud an cloud to test access controls
     * @return access controls for the cloud
     *
     * @since 1.252
     */
    public @Nonnull ACL getACL(@Nonnull Cloud cloud) {
        return getRootACL();
    }

    /**
     * Implementation can choose to provide different ACL for different {@link Node}s.
     * This can be used as a basis for more fine-grained access control.
     *
     * <p>
     * The default implementation returns {@link #getRootACL()}.
     *
     * <p>
     * Plugins may define this method
     * but should not call this method directly,
     * and should call {@link Node#getACL()} instead.
     *
     * @param node a node to test access controls
     * @return access controls for the node
     */
    public @Nonnull ACL getACL(@Nonnull Node node) {
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
     * users from using role names as group names (see HUDSON-2716 for such one such report.)
     *
     * @return
     *      never null.
     */
    public abstract @Nonnull Collection<String> getGroups();

    /**
     * Returns all the registered {@link AuthorizationStrategy} descriptors.
     */
    public static @Nonnull DescriptorExtensionList<AuthorizationStrategy,Descriptor<AuthorizationStrategy>> all() {
        return Jenkins.getInstance().<AuthorizationStrategy,Descriptor<AuthorizationStrategy>>getDescriptorList(AuthorizationStrategy.class);
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     *
     * @deprecated since 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<AuthorizationStrategy> LIST = new DescriptorList<AuthorizationStrategy>(AuthorizationStrategy.class);
    
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
        public @Nonnull ACL getRootACL() {
            return UNSECURED_ACL;
        }

        @Override
        public @Nonnull Collection<String> getGroups() {
            return Collections.emptySet();
        }

        private static final ACL UNSECURED_ACL = new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                return true;
            }
        };

        @Extension @Symbol("unsecured")
        public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
            @Override
            public String getDisplayName() {
                return Messages.AuthorizationStrategy_DisplayName();
            }

            @Override
            public @Nonnull AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return UNSECURED;
            }
        }
    }
}
