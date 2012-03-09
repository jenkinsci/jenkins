/*
 * The MIT License
 *
 * Copyright (c) 2012-, CloudBees, Inc.
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
package hudson.os.windows;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.os.windows.ManagedWindowsServiceLauncher.AccountInfo;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Encapsulates how to login (a part of {@link ManagedWindowsServiceLauncher}).
 * 
 * @author Kohsuke Kawaguchi
 * @author Vincent Latombe
 * @since 1.448
 */
public abstract class ManagedWindowsServiceAccount extends AbstractDescribableImpl<ManagedWindowsServiceAccount> implements ExtensionPoint {
    public abstract AccountInfo getAccount(ManagedWindowsServiceLauncher launcher);

    /**
     * Logs in with the local system user.
     * This is the default.
     */
    public static final class LocalSystem extends ManagedWindowsServiceAccount {
        @DataBoundConstructor
        public LocalSystem() {}

        @Override
        public AccountInfo getAccount(ManagedWindowsServiceLauncher launcher) {
            return null;
        }

        @Extension(ordinal=100)
        public static class DescriptorImpl extends Descriptor<ManagedWindowsServiceAccount> {
            @Override
            public String getDisplayName() {
                return "Use Local System User";
            }
        }
    }

    /**
     * Logs in with the administrator user account supplied in {@link ManagedWindowsServiceLauncher}.
     */
    public static final class Administrator extends ManagedWindowsServiceAccount {
        @DataBoundConstructor
        public Administrator() {}

        @Override
        public AccountInfo getAccount(ManagedWindowsServiceLauncher launcher) {
            return new AccountInfo(launcher.userName,Secret.toString(launcher.password));
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ManagedWindowsServiceAccount> {
            @Override
            public String getDisplayName() {
                return "Use Administrator account given above";
            }
        }
    }

    /**
     * Logs in with a separate user.
     */
    public static final class AnotherUser extends ManagedWindowsServiceAccount {
        public final String userName;
        public final Secret password;

        @DataBoundConstructor
        public AnotherUser(String userName, Secret password) {
            this.userName = userName;
            this.password = password;
        }

        @Override
        public AccountInfo getAccount(ManagedWindowsServiceLauncher launcher) {
            return new AccountInfo(userName,Secret.toString(password));
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<ManagedWindowsServiceAccount> {
            @Override
            public String getDisplayName() {
                return "Log on using a different account";
            }
        }
    }

}
