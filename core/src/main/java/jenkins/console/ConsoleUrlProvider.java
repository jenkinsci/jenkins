/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees, Inc.
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

package jenkins.console;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.User;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

/**
 * Extension point that allows implementations to redirect build console links to a specified URL.
 * <p>In order to produce links to console URLs in Jelly templates, use {@link Functions#getConsoleUrl}.
 * <p>Note: If you implement this API, consider providing a link to the classic console from within your console
 * visualization as a fallback, particularly if your visualization is not as general as the classic console, has
 * limitations that might be relevant in some cases, or requires advanced data that may be not exist for
 * failed or corrupted builds. For example, if you visualize Pipeline build logs using only {@code LogStorage.stepLog},
 * there will be log lines that will never show up in your visualization, or if your visualization traverses the
 * Pipeline flow graph, there may be various edge cases where your visualization does not work at all, but the classic
 * console view is unaffected.
 * @see Functions#getConsoleUrl
 * @since 2.433
 */
public interface ConsoleUrlProvider extends Describable<ConsoleUrlProvider> {
    @Restricted(NoExternalUse.class)
    Logger LOGGER = Logger.getLogger(ConsoleUrlProvider.class.getName());

    /**
     * Get a URL relative to the context path of Jenkins which should be used to link to the console for the specified build.
     * <p>Should only be used in the context of serving an HTTP request.
     * @param run the build
     * @return the URL for the console for the specified build, relative to the context of Jenkins (should not start with {@code /}), or {@code null}
     * if this implementation does not want to serve a special console view for this build.
     */
    @CheckForNull String getConsoleUrl(Run<?, ?> run);

    @Override
    default Descriptor<ConsoleUrlProvider> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Get a URL relative to the web server root which should be used to link to the console for the specified build.
     * <p>Should only be used in the context of serving an HTTP request.
     * <p>Use {@link Functions#getConsoleUrl} to obtain this link in a Jelly template.
     * @param run the build
     * @return the URL for the console for the specified build, relative to the web server root
     */
    static @NonNull String getRedirectUrl(Run<?, ?> run) {
        return Stapler.getCurrentRequest().getContextPath() + '/' + run.getConsoleUrl();
    }

    /**
     * Looks up the {@link #getConsoleUrl} value from the first provider to offer one.
     * @since 2.476
     */
    static @NonNull String consoleUrlOf(Run<?, ?> run) {
        final List<ConsoleUrlProvider> providers = new ArrayList<>();
        User currentUser = User.current();
        if (currentUser != null) {
            ConsoleUrlProviderUserProperty userProperty = currentUser.getProperty(ConsoleUrlProviderUserProperty.class);
            if (userProperty != null) {
                List<ConsoleUrlProvider> userProviders = userProperty.getProviders();
                if (userProviders != null) {
                    providers.addAll(userProviders);
                }
            }
        }
        // Global providers are always considered in case the user-configured providers are non-exhaustive.
        ConsoleUrlProviderGlobalConfiguration globalConfig = ConsoleUrlProviderGlobalConfiguration.get();
        List<ConsoleUrlProvider> globalProviders = globalConfig.getProviders();
        if (globalProviders != null) {
            providers.addAll(globalProviders);
        }
        String url = null;
        for (ConsoleUrlProvider provider : providers) {
            try {
                String tempUrl = provider.getConsoleUrl(run);
                if (tempUrl != null) {
                    if (new URI(tempUrl).isAbsolute()) {
                        LOGGER.warning(() -> "Ignoring absolute console URL " + tempUrl + " for " + run + " from " + provider.getClass());
                    } else if (tempUrl.startsWith("/")) {
                        LOGGER.warning(() -> "Ignoring URL " + tempUrl + " starting with / for " + run + " from " + provider.getClass());
                    } else {
                        // Found a valid non-null URL.
                        url = tempUrl;
                        break;
                    }
                }
            } catch (Exception e) { // Intentionally broad catch clause to guard against broken implementations.
                LOGGER.log(Level.WARNING, e, () -> "Error looking up console URL for " + run + " from " + provider.getClass());
            }
        }
        if (url == null) {
            // Reachable if DefaultConsoleUrlProvider is not one of the configured providers, including if no providers are configured at all.
            url = run.getUrl() + "console";
        }
        return url;
    }

    /**
     * Check whether there are at least two {@link ConsoleUrlProvider} implementations available.
     * @return {@code true} if there are at least two {@link ConsoleUrlProvider} implementations available, {@code false} otherwise.
     */
    static boolean isEnabled() {
        // No point showing related configuration pages if the only option is ConsoleUrlProvider.Default.
        return Jenkins.get().getDescriptorList(ConsoleUrlProvider.class).size() > 1;
    }
}
